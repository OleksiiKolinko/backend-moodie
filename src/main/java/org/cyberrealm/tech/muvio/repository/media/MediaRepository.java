package org.cyberrealm.tech.muvio.repository.media;

import java.util.Optional;
import java.util.Set;
import org.cyberrealm.tech.muvio.dto.MediaBaseDto;
import org.cyberrealm.tech.muvio.dto.MediaDtoFromDb;
import org.cyberrealm.tech.muvio.dto.MediaDtoWithCastFromDb;
import org.cyberrealm.tech.muvio.dto.PosterDto;
import org.cyberrealm.tech.muvio.dto.TitleDto;
import org.cyberrealm.tech.muvio.model.Media;
import org.cyberrealm.tech.muvio.model.Type;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaRepository extends MongoRepository<Media, String>, MediaRepositoryCustom {

    @Aggregation(pipeline = {
            "{ '$sample': { 'size': ?0 } }"
    })
    Set<MediaDtoFromDb> getAllLuck(int size);

    @Aggregation(pipeline = {
            "{ '$match': { 'type': ?0, 'genres': ?1, 'releaseYear': { $gte: ?2 } } }",
            "{ '$sort': { 'rating': -1 } }"
    })
    Slice<MediaBaseDto> findMoviesByTypeGenreAndYears(Type type, String genre, int minYear,
                                                      Pageable pageable);

    Optional<MediaDtoFromDb> findMovieById(String id);

    Slice<MediaDtoWithCastFromDb> findByTopListsContaining(String topList, Pageable pageable);

    @Query(value = "{ 'posterPath': { '$ne': null } }", fields = "{ 'id': 1, 'posterPath': 1 }")
    Slice<PosterDto> findAllPosters(Pageable pageable);

    @Query(value = "{}", fields = "{ 'id': 1, 'title': 1 }")
    Slice<TitleDto> findAllTitles(Pageable pageable);

    MediaDtoFromDb findByTitle(String title);
}
