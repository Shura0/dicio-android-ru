package org.dicio.dicio_android.components.processing;

import org.dicio.component.IntermediateProcessor;
import org.dicio.component.standard.StandardResult;
import org.dicio.dicio_android.components.output.LyricsOutput;
import org.dicio.dicio_android.util.ConnectionUtils;
import org.dicio.dicio_android.util.RegexUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.unbescape.javascript.JavaScriptEscape;
import org.unbescape.json.JsonEscape;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.dicio.dicio_android.Sentences_en.lyrics;

public class GeniusProcessor implements IntermediateProcessor<StandardResult, LyricsOutput.Data> {

    // replace "songs" with "multi" to get all kinds of results and not just songs
    private static final String geniusSearchUrl = "https://genius.com/api/search/songs?q=";
    private static final String geniusLyricsUrl = "https://genius.com/songs/";
    private static final Pattern lyricsPattern =
            Pattern.compile("document\\.write\\(JSON\\.parse\\('(.+)'\\)\\)");
    private static final Pattern newlinePattern =
            Pattern.compile("\\s*(\\\\n)?\\s*\\{#%\\)\\s*");

    @Override
    public LyricsOutput.Data process(final StandardResult data, final Locale locale)
            throws Exception {
        final String songName = data.getCapturingGroup(lyrics.song);
        final JSONObject search = ConnectionUtils.getPageJson(
                geniusSearchUrl + ConnectionUtils.urlEncode(songName));
        final JSONArray searchHits =
                search.getJSONObject("response").getJSONArray("sections")
                        .getJSONObject(0).getJSONArray("hits");

        final LyricsOutput.Data result = new LyricsOutput.Data();
        if (searchHits.length() == 0) {
            result.failed = true;
            result.title = songName;
            return result;
        }

        final JSONObject song = searchHits.getJSONObject(0).getJSONObject("result");
        result.title = song.getString("title");
        result.artist = song.getJSONObject("primary_artist").getString("name");


        String lyricsHtml =
                ConnectionUtils.getPage(
                        geniusLyricsUrl + song.getInt("id") + "/embed.js");
        lyricsHtml = RegexUtils.matchGroup1(lyricsPattern, lyricsHtml);
        lyricsHtml = JsonEscape.unescapeJson(JavaScriptEscape.unescapeJavaScript(lyricsHtml));

        final Document lyricsDocument = Jsoup.parse(lyricsHtml);
        final Elements elements = lyricsDocument.select("div[class=rg_embed_body]");
        elements.select("br").append("{#%)");
        result.lyrics = RegexUtils.replaceAll(newlinePattern, elements.text(), "\n");

        return result;
    }
}
