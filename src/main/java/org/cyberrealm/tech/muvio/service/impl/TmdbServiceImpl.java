package org.cyberrealm.tech.muvio.service.impl;

import info.movito.themoviedbapi.TmdbApi;
import info.movito.themoviedbapi.TmdbMovies;
import info.movito.themoviedbapi.model.core.Genre;
import info.movito.themoviedbapi.model.core.Movie;
import info.movito.themoviedbapi.model.core.Review;
import info.movito.themoviedbapi.model.core.ReviewResultsPage;
import info.movito.themoviedbapi.model.core.image.Artwork;
import info.movito.themoviedbapi.model.core.video.VideoResults;
import info.movito.themoviedbapi.model.movies.Credits;
import info.movito.themoviedbapi.model.movies.KeywordResults;
import info.movito.themoviedbapi.model.movies.MovieDb;
import info.movito.themoviedbapi.model.movies.ReleaseInfo;
import info.movito.themoviedbapi.tools.TmdbException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.cyberrealm.tech.muvio.exception.TmdbServiceException;
import org.cyberrealm.tech.muvio.service.TmdbService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TmdbServiceImpl implements TmdbService {
    public static final String NOT_FOUND = "Not found";
    public static final String IMAGE_PATH = "https://image.tmdb.org/t/p/w500";
    private static final String YOUTUBE_PATH = "https://www.youtube.com/watch?v=";
    private static final String TRAILER = "Trailer";
    private static final String TEASER = "Teaser";
    private static final int FIRST_PAGE = 1;
    private static final int LAST_PAGE = 10;
    private static final int MAX_NUMBER_OF_RECORDS = 6;
    private final TmdbApi tmdbApi;

    @Override
    public TmdbMovies getTmdbMovies() {
        return tmdbApi.getMovies();
    }

    @Override
    public List<Genre> fetchGenres(String language) {
        try {
            return tmdbApi.getGenre().getMovieList(language);
        } catch (TmdbException e) {
            throw new TmdbServiceException("Failed to fetch genres from TMDB", e);
        }
    }

    @Override
    public List<Movie> fetchPopularMovies(int fromPage, int toPage, String language,
                                          String location) {
        List<Movie> allMovies = List.of();
        try {
            for (int page = fromPage; page <= toPage; page++) {
                allMovies = tmdbApi.getMovieLists().getPopular(language, page, location)
                        .getResults();
            }
        } catch (TmdbException e) {
            throw new TmdbServiceException("Failed to fetch popular movies from TMDB", e);
        }
        return allMovies;
    }

    @Override
    public MovieDb fetchMovieDetails(TmdbMovies tmdbMovies, int movieId, String language) {
        try {
            return tmdbMovies.getDetails(movieId, language);
        } catch (TmdbException e) {
            throw new TmdbServiceException("Can't load movie details by movieId: " + movieId
                    + e.getMessage());
        }
    }

    @Override
    public Credits fetchMovieCredits(TmdbMovies tmdbMovies, int movieId, String language) {
        try {
            return tmdbMovies.getCredits(movieId, language);
        } catch (TmdbException e) {
            throw new TmdbServiceException("Can't load credits by movieId: " + movieId
                    + e.getMessage());
        }
    }

    @Override
    public String fetchTrailer(TmdbMovies tmdbMovies, int movieId, String language) {
        try {
            return getTrailerLink(tmdbMovies.getVideos(movieId, language), TRAILER)
                    .orElse(getTrailerLink(tmdbMovies.getVideos(movieId, language), TEASER)
                            .orElse(NOT_FOUND));
        } catch (TmdbException e) {
            throw new TmdbServiceException("Failed to fetch trailer from TMDB", e);
        }
    }

    @Override
    public Set<String> fetchPhotos(TmdbMovies tmdbMovies, String language, int movieId) {
        try {
            return tmdbMovies.getImages(movieId, language).getBackdrops().stream()
                    .sorted(Comparator.comparing(Artwork::getVoteAverage))
                    .limit(MAX_NUMBER_OF_RECORDS)
                    .peek(artwork -> artwork.setFilePath(IMAGE_PATH + artwork.getFilePath()))
                    .map(Artwork::getFilePath)
                    .collect(Collectors.toSet());
        } catch (TmdbException e) {
            throw new TmdbServiceException("Failed to fetch photos from TMDB", e);
        }
    }

    @Override
    public KeywordResults fetchKeywords(TmdbMovies tmdbMovies, int movieId) {
        try {
            return tmdbMovies.getKeywords(movieId);
        } catch (TmdbException e) {
            throw new TmdbServiceException("Failed to fetch keywords from TMDB", e);
        }
    }

    @Override
    public List<ReleaseInfo> fetchReleaseInfo(TmdbMovies tmdbMovies, int movieId) {
        try {
            return tmdbMovies.getReleaseDates(movieId).getResults();
        } catch (TmdbException e) {
            throw new TmdbServiceException("Failed to fetch release info from TMDB", e);
        }
    }

    @Override
    public List<Review> fetchMovieReviews(TmdbMovies tmdbMovies, String language, int movieId) {
        final List<Review> allReviews = new ArrayList<>();
        try {
            for (int page = FIRST_PAGE; page <= LAST_PAGE; page++) {
                ReviewResultsPage reviewPage = tmdbMovies.getReviews(movieId, language, page);
                if (reviewPage != null && reviewPage.getResults() != null) {
                    allReviews.addAll(reviewPage.getResults());
                }
            }
        } catch (TmdbException e) {
            throw new TmdbServiceException(
                    "Failed to fetch reviews from TMDB: " + e.getMessage(), e);
        }
        return allReviews.stream()
                .map(this::updateReviewAvatar)
                .collect(Collectors.toList());
    }

    private Optional<String> getTrailerLink(VideoResults videos, String type) {
        return videos.getResults().stream()
                .filter(video -> video.getType().equals(type))
                .map(trailer -> YOUTUBE_PATH + trailer.getKey())
                .findFirst();
    }

    private Review updateReviewAvatar(Review review) {
        if (review.getAuthorDetails() != null) {
            String avatarPath = review.getAuthorDetails().getAvatarPath();
            if (avatarPath != null) {
                review.getAuthorDetails().setAvatarPath(IMAGE_PATH + avatarPath);
            } else {
                review.getAuthorDetails().setAvatarPath(NOT_FOUND);
            }
        }
        return review;
    }
}
