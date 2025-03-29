package org.cyberrealm.tech.muvio.config;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.TmdbMovieLists;
import info.movito.themoviedbapi.TmdbMovies;
import info.movito.themoviedbapi.TmdbSearch;
import info.movito.themoviedbapi.TmdbTvSeries;
import info.movito.themoviedbapi.TmdbTvSeriesLists;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import org.cyberrealm.tech.muvio.exception.NetworkRequestException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.retry.annotation.EnableRetry;

@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@EnableRetry
@Configuration
public class SecurityConfig {
    private static final String NAME = "name";
    private static final String SHOW_NAME = "Show Name";
    private static final String RESULTS = "results";
    private static final String BINDINGS = "bindings";
    private static final String AWARD_WORK_LABEL = "awardWorkLabel";
    private static final String VALUE = "value";
    private static final int ZERO = 0;
    private static final String QUERY = "?query=";
    private static final String FORMAT_JSON = "&format=json";
    private static final String GET = "GET";
    private static final String PATTERN_A = "\\A";
    private static final String SELECT_TABLE = "table.wikitable";
    private static final String ROWS_TR = "tr";
    private static final String ROWS_TD = "td";
    private static final String ROWS_I = "i";
    private static final String TITLE = "title";
    @Value("${tmdb.api.key}")
    private String apiKey;
    @Value("${top250.movie.url}")
    private String top250MovieUrl;
    @Value("${top250.tvShow.url}")
    private String top250TvShowUrl;
    @Value("${sparql.endpoint}")
    private String sparqlEndpoint;
    @Value("${sparql.query}")
    private String sparqlQuery;
    @Value("${emmy.winners.url}")
    private String emmyWinnersUrl;

    @Bean
    public TmdbApi tmdbApi() {
        return new TmdbApi(apiKey);
    }

    @Bean
    public TmdbMovies tmdbMovies(TmdbApi tmdbApi) {
        return tmdbApi.getMovies();
    }

    @Bean
    public TmdbTvSeries tmdbTvSeries(TmdbApi tmdbApi) {
        return tmdbApi.getTvSeries();
    }

    @Bean
    public TmdbMovieLists tmdbMovieLists(TmdbApi tmdbApi) {
        return tmdbApi.getMovieLists();
    }

    @Bean
    public TmdbTvSeriesLists tmdbTvSeriesLists(TmdbApi tmdbApi) {
        return tmdbApi.getTvSeriesLists();
    }

    @Bean
    public TmdbSearch tmdbSearch(TmdbApi tmdbApi) {
        return tmdbApi.getSearch();
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public Set<String> imdbTop250Movies(HttpClient httpClient) {
        return getTopSet(top250MovieUrl, NAME, httpClient);
    }

    @Bean
    public Set<String> imdbTop250TvShows(HttpClient httpClient) {
        return getTopSet(top250TvShowUrl, SHOW_NAME, httpClient);
    }

    @Bean
    public Set<String> oscarWinningMedia() {
        final Set<String> oscarWorks = new HashSet<>();
        final JSONArray results = new JSONObject(executeSparqlQuery()).getJSONObject(RESULTS)
                .getJSONArray(BINDINGS);
        for (int i = ZERO; i < results.length(); i++) {
            final JSONObject filmObj = results.getJSONObject(i);
            if (filmObj.has(AWARD_WORK_LABEL)) {
                final JSONObject awardWorkLabel = filmObj.getJSONObject(AWARD_WORK_LABEL);
                if (awardWorkLabel.has(VALUE)) {
                    final String title = awardWorkLabel.getString(VALUE);
                    oscarWorks.add(title);
                }
            }
        }
        return oscarWorks;
    }

    @Bean
    public Set<String> emmyWinningMedia() {
        Set<String> winners = new HashSet<>();
        try {
            Document doc = Jsoup.connect(emmyWinnersUrl).get();
            Elements tables = doc.select(SELECT_TABLE);
            for (Element table : tables) {
                parseTableRows(table, winners);
            }
        } catch (IOException e) {
            throw new NetworkRequestException("Error during Emmy winning request", e);
        }
        return winners;
    }

    private void parseTableRows(Element table, Set<String> winners) {
        Elements rows = table.select(ROWS_TR);
        for (Element row : rows) {
            Elements columns = row.select(ROWS_TD);
            for (Element column : columns) {
                extractShowNames(column, winners);
            }
        }
    }

    private void extractShowNames(Element column, Set<String> winners) {
        Elements elements = column.select(ROWS_I);
        for (Element element : elements) {
            String showName = element.getElementsByAttribute(TITLE).text().trim();
            if (!showName.isEmpty()) {
                winners.add(showName);
            }
        }
    }

    private Set<String> getTopSet(String url, String fieldName, HttpClient client) {
        final Set<String> imdbTop250 = new HashSet<>();
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            final HttpResponse<String> response = client
                    .send(request, HttpResponse.BodyHandlers.ofString());
            final String json = response.body();
            final ObjectMapper mapper = new ObjectMapper();
            final List<Map<String, Object>> movies = mapper
                    .readValue(json, new TypeReference<>() {});
            for (Map<String, Object> movie : movies) {
                imdbTop250.add((String) movie.get(fieldName));
            }
        } catch (IOException | InterruptedException e) {
            throw new NetworkRequestException("Failed to fetch the IMDB Top 250 page", e);
        }
        return imdbTop250;
    }

    private String executeSparqlQuery() {
        try {
            final String queryUrl = sparqlEndpoint + QUERY + URLEncoder
                    .encode(sparqlQuery, StandardCharsets.UTF_8) + FORMAT_JSON;
            final HttpURLConnection connection = (HttpURLConnection) new URI(queryUrl).toURL()
                    .openConnection();
            connection.setRequestMethod(GET);
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                return scanner.useDelimiter(PATTERN_A).next();
            }
        } catch (IOException | URISyntaxException e) {
            throw new NetworkRequestException("Error during SPARQL query execution", e);
        }
    }
}
