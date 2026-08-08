package com.fyiplayer.app.source.newpipe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder;
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptDecoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Local sig/n decoder: fetches YouTube's own base.js player and runs the deobfuscation functions
 * inside {@link SharedWebViewRuntime}. Registered via
 * {@link YoutubeApiDecoder#setLocalDecoder} so the bundled PipePipeExtractor never needs its
 * remote decoder API (api.pipepipe.dev) -- that dependency is exactly what broke playback of
 * ciphered-signature videos on networks where the API host does not resolve: an undecoded
 * sig/n parameter makes googlevideo answer 403 for the whole video.
 *
 * <p>Ported from PipePipe's WebViewJavaScriptDecoder (GPLv3, same license family as the bundled
 * extractor); network calls go through the app's own OkHttp client instead of PipePipe's
 * DownloaderImpl.</p>
 */
public final class WebViewJsDecoder implements YoutubeJavaScriptDecoder {
    private static final String TAG = "WebViewJsDecoder";
    private static final String PLAYER_URL =
            "https://www.youtube.com/s/player/%s/player_ias.vflset/en_US/base.js";
    private static final String IFRAME_URL = "https://www.youtube.com/iframe_api";
    private static final long TIMEOUT_MS = 30_000L;
    private static final long PLAYER_CACHE_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final String PLAYER_CACHE_PREFIX = "youtube-player-";
    private static final String PLAYER_CACHE_PREFS = "youtube-player-cache";
    private static final Pattern PLAYER_PATTERN = Pattern.compile(
            "player\\\\/([a-z0-9]{8})\\\\/");
    private static final Pattern SIGNATURE_TIMESTAMP_PATTERN = Pattern.compile(
            "signatureTimestamp\\s*[:=]\\s*(\\d+)");

    private final Context context;
    private final SharedWebViewRuntime runtime;
    private final SharedPreferences preferences;
    private final OkHttpClient httpClient;
    private boolean ready;
    private String loadedPlayerId;
    private String preparedPlayerId;
    private String preparedPlayerCode;

    public WebViewJsDecoder(final Context context, final OkHttpClient httpClient) {
        this.context = context.getApplicationContext();
        this.httpClient = httpClient;
        runtime = SharedWebViewRuntime.get(this.context);
        preferences = this.context.getSharedPreferences(
                PLAYER_CACHE_PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public synchronized PlayerData getPlayerData(final String videoId)
            throws ParsingException {
        final long start = SystemClock.elapsedRealtime();
        try {
            final String cachedPlayerId = preferences.getString("playerId", null);
            final int cachedTimestamp = preferences.getInt("signatureTimestamp", 0);
            final long expiresAt = preferences.getLong("expiresAt", 0);
            if (cachedPlayerId != null && cachedTimestamp != 0
                    && System.currentTimeMillis() < expiresAt) {
                final File cachedFile = playerFile(cachedPlayerId);
                if (cachedFile.isFile()) {
                    final String playerCode = readFile(cachedFile);
                    preparedPlayerId = cachedPlayerId;
                    preparedPlayerCode = playerCode;
                    logMetadata(start, "disk", cachedPlayerId, playerCode.length());
                    return new PlayerData(cachedPlayerId, cachedTimestamp);
                }
            }
            final Matcher playerMatcher = PLAYER_PATTERN.matcher(httpGet(IFRAME_URL));
            if (!playerMatcher.find()) {
                throw new ParsingException("Could not find YouTube player ID");
            }
            final String playerId = playerMatcher.group(1);
            final File playerFile = playerFile(playerId);
            final boolean diskCached = playerFile.isFile();
            final String playerCode = diskCached
                    ? readFile(playerFile) : fetchAndCachePlayer(playerId, playerFile);
            final Matcher timestampMatcher = SIGNATURE_TIMESTAMP_PATTERN.matcher(playerCode);
            if (!timestampMatcher.find()) {
                throw new ParsingException("Could not find signature timestamp");
            }
            preparedPlayerId = playerId;
            preparedPlayerCode = playerCode;
            final int signatureTimestamp = Integer.parseInt(timestampMatcher.group(1));
            preferences.edit()
                    .putString("playerId", playerId)
                    .putInt("signatureTimestamp", signatureTimestamp)
                    .putLong("expiresAt", System.currentTimeMillis() + PLAYER_CACHE_TTL_MS)
                    .apply();
            logMetadata(start, diskCached ? "disk-refresh" : "network",
                    playerId, playerCode.length());
            return new PlayerData(playerId, signatureTimestamp);
        } catch (final ParsingException e) {
            throw e;
        } catch (final Exception e) {
            throw new ParsingException("Could not load local player metadata", e);
        }
    }

    private File playerFile(final String playerId) {
        return new File(context.getFilesDir(), PLAYER_CACHE_PREFIX + playerId + ".js");
    }

    private static void logMetadata(final long start, final String source,
                                    final String playerId, final int length) {
        Log.i(TAG, "metadata=" + (SystemClock.elapsedRealtime() - start) + "ms"
                + " source=" + source + " player=" + playerId + " chars=" + length);
    }

    @Override
    public synchronized YoutubeApiDecoder.BatchDecodeResult decodeBatch(
            final String playerId,
            final List<String> signatures,
            final List<String> throttlingParameters)
            throws ParsingException {
        ensureReady();
        final JSONObject request = new JSONObject();
        final JSONArray requests = new JSONArray();
        try {
            if (throttlingParameters != null && !throttlingParameters.isEmpty()) {
                requests.put(makeRequest("n", throttlingParameters));
            }
            if (signatures != null && !signatures.isEmpty()) {
                requests.put(makeRequest("sig", signatures));
            }
            request.put("playerId", playerId);
            request.put("requests", requests);
            final long localStart = SystemClock.elapsedRealtime();
            if (!playerId.equals(loadedPlayerId)) {
                final String playerCode = playerId.equals(preparedPlayerId)
                        && preparedPlayerCode != null
                        ? preparedPlayerCode : fetchPlayer(playerId);
                uploadPlayer(playerId, playerCode);
            }
            if (playerId.equals(preparedPlayerId)) {
                preparedPlayerId = null;
                preparedPlayerCode = null;
            }
            final JSONObject localJson = evaluate(request);
            final long localMs = SystemClock.elapsedRealtime() - localStart;
            final YoutubeApiDecoder.BatchDecodeResult local = parseResult(localJson,
                    throttlingParameters != null && !throttlingParameters.isEmpty(),
                    signatures != null && !signatures.isEmpty());
            Log.i(TAG, "player=" + playerId
                    + " cold=" + localJson.optBoolean("cold")
                    + " v8=" + localJson.optLong("elapsedMs") + "ms"
                    + " local=" + localMs + "ms");
            return local;
        } catch (final ParsingException e) {
            Log.e(TAG, "decode failed for player=" + playerId);
            throw e;
        } catch (final Exception e) {
            Log.e(TAG, "decode failed for player=" + playerId);
            throw new ParsingException("Local WebView decoding failed", e);
        }
    }

    private void ensureReady() throws ParsingException {
        if (ready) {
            return;
        }
        try {
            runtime.ensureReady(TIMEOUT_MS, "JS runtime initialization");
            runtime.evaluateJavascriptBlocking("if(!Object.hasOwn){Object.hasOwn=function(o,p){"
                            + "return Object.prototype.hasOwnProperty.call(o,p)}};"
                            + "if(!Array.prototype.at){Array.prototype.at=function(i){"
                            + "i=Math.trunc(i)||0;if(i<0)i+=this.length;return this[i]}};"
                            + runtime.loadAsset("ejs/yt.solver.polyfills.es5.js")
                            + runtime.loadAsset("ejs/yt.solver.lib.es5.min.js")
                            + ";var meriyah=lib.meriyah,astring=lib.astring;true",
                    TIMEOUT_MS, "JS library initialization");
            final String value = runtime.evaluateJavascriptBlocking(
                    runtime.loadAsset("ejs/yt.solver.core.es5.min.js") + ";typeof jsc==='function'",
                    TIMEOUT_MS, "JS core initialization");
            if (!"true".equals(value)) {
                throw new ParsingException("EJS initialization returned " + value);
            }
        } catch (final ParsingException e) {
            throw e;
        } catch (final Exception e) {
            throw new ParsingException("Could not initialize WebView JS runtime", e);
        }
        ready = true;
    }

    private JSONObject evaluate(final JSONObject request) throws Exception {
        final String script = "(function(i){try{var s=performance.now(),cold="
                + "window.__ejsPlayerId!==i.playerId;if(cold){var r=jsc({type:'player',"
                + "player:window.__ejsPlayer,requests:i.requests,output_preprocessed:true});"
                + "window.__ejsSolvers={};Function('_result',r.preprocessed_player)("
                + "window.__ejsSolvers);window.__ejsPlayerId=i.playerId;window.__ejsPlayer=null;"
                + "delete r.preprocessed_player}else{r={type:'result',responses:i.requests.map("
                + "function(q){var f=window.__ejsSolvers[q.type];if(!f)return{type:'error',"
                + "error:'Failed to extract '+q.type+' function'};try{var d={};q.challenges."
                + "forEach(function(v){d[v]=f(v)});return{type:'result',data:d}}catch(e){return{"
                + "type:'error',error:e instanceof Error?e.message+'\\n'+e.stack:String(e)}}})}}"
                + "return JSON.stringify({"
                + "ok:true,cold:cold,elapsedMs:Math.round(performance.now()-s),result:r})}catch(e){"
                + "return JSON.stringify({ok:false,error:String(e),stack:e&&e.stack})}})("
                + request + ")";
        final String value = runtime.evaluateJavascriptBlocking(script, TIMEOUT_MS,
                "JS decoding");
        if (value == null || "null".equals(value)) {
            throw new IllegalStateException("JS runtime returned null");
        }
        final JSONObject wrapper = new JSONObject(new JSONArray("[" + value + "]").getString(0));
        if (!wrapper.optBoolean("ok")) {
            throw new ParsingException("JS error: " + wrapper.optString("error")
                    + "\n" + wrapper.optString("stack"));
        }
        return wrapper;
    }

    private void uploadPlayer(final String playerId, final String player) throws Exception {
        evaluateRaw("window.__ejsPlayer=''");
        final int chunkSize = 128 * 1024;
        for (int start = 0; start < player.length(); start += chunkSize) {
            final int end = Math.min(start + chunkSize, player.length());
            evaluateRaw("window.__ejsPlayer+=" + JSONObject.quote(player.substring(start, end)));
        }
        evaluateRaw("if(window.__ejsPlayer.length!==" + player.length()
                + "){throw new Error(window.__ejsPlayer.length)}");
        loadedPlayerId = playerId;
    }

    private void evaluateRaw(final String script) throws Exception {
        final String value = runtime.evaluateJavascriptBlocking(
                "(function(){try{" + script + ";return ''}catch(e){return String(e)}})()",
                TIMEOUT_MS, "JS player upload");
        if (value != null && !"\"\"".equals(value)) {
            throw new ParsingException("Could not upload player to JS runtime: " + value);
        }
    }

    private String httpGet(final String url) throws IOException {
        final Request request = new Request.Builder().url(url)
                .header("User-Agent", SharedWebViewRuntime.USER_AGENT)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("http " + response.code());
            }
            return response.body().string();
        }
    }

    private String fetchPlayer(final String playerId) throws ParsingException {
        try {
            return httpGet(String.format(PLAYER_URL, playerId));
        } catch (final Exception e) {
            throw new ParsingException("Could not fetch YouTube player", e);
        }
    }

    private String fetchAndCachePlayer(final String playerId, final File playerFile)
            throws ParsingException {
        final String playerCode = fetchPlayer(playerId);
        final File temporaryFile = new File(playerFile.getPath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporaryFile)) {
            output.write(playerCode.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            temporaryFile.delete();
            throw new ParsingException("Could not cache YouTube player", e);
        }
        if (!temporaryFile.renameTo(playerFile)) {
            temporaryFile.delete();
            throw new ParsingException("Could not replace cached YouTube player");
        }
        final File[] files = context.getFilesDir().listFiles();
        if (files != null) {
            for (final File file : files) {
                if (file.getName().startsWith(PLAYER_CACHE_PREFIX)
                        && !file.equals(playerFile)) {
                    file.delete();
                }
            }
        }
        return playerCode;
    }

    private static String readFile(final File file) throws ParsingException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (final Exception e) {
            throw new ParsingException("Could not read cached YouTube player", e);
        }
    }

    private static YoutubeApiDecoder.BatchDecodeResult parseResult(final JSONObject wrapper,
                                                                   final boolean hasN,
                                                                   final boolean hasSig)
            throws Exception {
        final JSONObject result = wrapper.getJSONObject("result");
        if (!"result".equals(result.optString("type"))) {
            throw new ParsingException("Decoder returned " + result);
        }
        final Map<String, String> signatures = new HashMap<>();
        final Map<String, String> throttlingParameters = new HashMap<>();
        final JSONArray responses = result.getJSONArray("responses");
        for (int i = 0; i < responses.length(); i++) {
            final JSONObject response = responses.getJSONObject(i);
            if (!"result".equals(response.optString("type"))) {
                throw new ParsingException("Decoder response returned " + response);
            }
            final Map<String, String> destination = hasN && (i == 0 || !hasSig)
                    ? throttlingParameters : signatures;
            final JSONObject data = response.getJSONObject("data");
            final java.util.Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                final String key = keys.next();
                destination.put(key, data.getString(key));
            }
        }
        return new YoutubeApiDecoder.BatchDecodeResult(signatures, throttlingParameters);
    }

    private static JSONObject makeRequest(final String type, final List<String> values)
            throws Exception {
        return new JSONObject().put("type", type).put("challenges", new JSONArray(values));
    }
}
