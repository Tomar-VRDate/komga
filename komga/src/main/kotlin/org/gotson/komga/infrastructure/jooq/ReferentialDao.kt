package org.gotson.komga.infrastructure.jooq

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.persistence.ReferentialRepository
import org.gotson.komga.jooq.Tables
import org.gotson.komga.jooq.tables.records.BookMetadataAggregationAuthorRecord
import org.gotson.komga.jooq.tables.records.BookMetadataAuthorRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL.lower
import org.jooq.impl.DSL.select
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ReferentialDao(
  private val dsl: DSLContext
) : ReferentialRepository {

  private val a = Tables.BOOK_METADATA_AUTHOR
  private val sd = Tables.SERIES_METADATA
  private val bma = Tables.BOOK_METADATA_AGGREGATION
  private val bmaa = Tables.BOOK_METADATA_AGGREGATION_AUTHOR
  private val s = Tables.SERIES
  private val b = Tables.BOOK
  private val g = Tables.SERIES_METADATA_GENRE
  private val bt = Tables.BOOK_METADATA_TAG
  private val st = Tables.SERIES_METADATA_TAG
  private val cs = Tables.COLLECTION_SERIES

  override fun findAllAuthorsByName(search: String, filterOnLibraryIds: Collection<String>?): List<Author> =
    dsl.selectDistinct(a.NAME, a.ROLE)
      .from(a)
      .apply { filterOnLibraryIds?.let { leftJoin(b).on(a.BOOK_ID.eq(b.ID)) } }
      .where(a.NAME.containsIgnoreCase(search))
      .apply { filterOnLibraryIds?.let { and(b.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(a.NAME), a.ROLE)
      .fetchInto(a)
      .map { it.toDomain() }

  override fun findAllAuthorsByNameAndLibrary(search: String, libraryId: String, filterOnLibraryIds: Collection<String>?): List<Author> =
    dsl.selectDistinct(bmaa.NAME, bmaa.ROLE)
      .from(bmaa)
      .leftJoin(s).on(bmaa.SERIES_ID.eq(s.ID))
      .where(bmaa.NAME.containsIgnoreCase(search))
      .and(s.LIBRARY_ID.eq(libraryId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(bmaa.NAME), bmaa.ROLE)
      .fetchInto(bmaa)
      .map { it.toDomain() }

  override fun findAllAuthorsByNameAndCollection(search: String, collectionId: String, filterOnLibraryIds: Collection<String>?): List<Author> =
    dsl.selectDistinct(bmaa.NAME, bmaa.ROLE)
      .from(bmaa)
      .leftJoin(cs).on(bmaa.SERIES_ID.eq(cs.SERIES_ID))
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(bmaa.SERIES_ID.eq(s.ID)) } }
      .where(bmaa.NAME.containsIgnoreCase(search))
      .and(cs.COLLECTION_ID.eq(collectionId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(bmaa.NAME), bmaa.ROLE)
      .fetchInto(bmaa)
      .map { it.toDomain() }

  override fun findAllAuthorsByNameAndSeries(search: String, seriesId: String, filterOnLibraryIds: Collection<String>?): List<Author> =
    dsl.selectDistinct(bmaa.NAME, bmaa.ROLE)
      .from(bmaa)
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(bmaa.SERIES_ID.eq(s.ID)) } }
      .where(bmaa.NAME.containsIgnoreCase(search))
      .and(bmaa.SERIES_ID.eq(seriesId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(bmaa.NAME), bmaa.ROLE)
      .fetchInto(bmaa)
      .map { it.toDomain() }

  override fun findAllAuthorsByName(search: String, role: String?, filterOnLibraryIds: Collection<String>?, pageable: Pageable): Page<Author> {
    return findAuthorsByName(search, role, filterOnLibraryIds, pageable, null)
  }

  override fun findAllAuthorsByNameAndLibrary(search: String, role: String?, libraryId: String, filterOnLibraryIds: Collection<String>?, pageable: Pageable): Page<Author> {
    return findAuthorsByName(search, role, filterOnLibraryIds, pageable, FilterBy(FilterByType.LIBRARY, libraryId))
  }

  override fun findAllAuthorsByNameAndCollection(search: String, role: String?, collectionId: String, filterOnLibraryIds: Collection<String>?, pageable: Pageable): Page<Author> {
    return findAuthorsByName(search, role, filterOnLibraryIds, pageable, FilterBy(FilterByType.COLLECTION, collectionId))
  }

  override fun findAllAuthorsByNameAndSeries(search: String, role: String?, seriesId: String, filterOnLibraryIds: Collection<String>?, pageable: Pageable): Page<Author> {
    return findAuthorsByName(search, role, filterOnLibraryIds, pageable, FilterBy(FilterByType.SERIES, seriesId))
  }

  private enum class FilterByType {
    LIBRARY,
    COLLECTION,
    SERIES,
  }

  private data class FilterBy(
    val type: FilterByType,
    val id: String,
  )

  private fun findAuthorsByName(search: String, role: String?, filterOnLibraryIds: Collection<String>?, pageable: Pageable, filterBy: FilterBy?): Page<Author> {
    val query = dsl.selectDistinct(bmaa.NAME, bmaa.ROLE)
      .from(bmaa)
      .apply { if (filterOnLibraryIds != null || filterBy?.type == FilterByType.LIBRARY) leftJoin(s).on(bmaa.SERIES_ID.eq(s.ID)) }
      .apply { if (filterBy?.type == FilterByType.COLLECTION) leftJoin(cs).on(bmaa.SERIES_ID.eq(cs.SERIES_ID)) }
      .where(bmaa.NAME.containsIgnoreCase(search))
      .apply { role?.let { and(bmaa.ROLE.eq(role)) } }
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .apply {
        filterBy?.let {
          when (it.type) {
            FilterByType.LIBRARY -> and(s.LIBRARY_ID.eq(it.id))
            FilterByType.COLLECTION -> and(cs.COLLECTION_ID.eq(it.id))
            FilterByType.SERIES -> and(bmaa.SERIES_ID.eq(it.id))
          }
        }
      }

    val count = dsl.fetchCount(query)

    val items = query
      .orderBy(lower(bmaa.NAME), bmaa.ROLE)
      .apply { if (pageable.isPaged) limit(pageable.pageSize).offset(pageable.offset) }
      .fetchInto(a)
      .map { it.toDomain() }

    val pageSort = Sort.by("name")
    return PageImpl(
      items,
      if (pageable.isPaged) PageRequest.of(pageable.pageNumber, pageable.pageSize, pageSort)
      else PageRequest.of(0, maxOf(count, 20), pageSort),
      count.toLong()
    )
  }

  override fun findAllAuthorsNamesByName(search: String, filterOnLibraryIds: Collection<String>?): List<String> =
    dsl.selectDistinct(a.NAME)
      .from(a)
      .apply { filterOnLibraryIds?.let { leftJoin(b).on(a.BOOK_ID.eq(b.ID)) } }
      .where(a.NAME.containsIgnoreCase(search))
      .apply { filterOnLibraryIds?.let { and(b.LIBRARY_ID.`in`(it)) } }
      .orderBy(a.NAME)
      .fetch(a.NAME)

  override fun findAllAuthorsRoles(filterOnLibraryIds: Collection<String>?): List<String> =
    dsl.selectDistinct(a.ROLE)
      .from(a)
      .apply {
        filterOnLibraryIds?.let {
          leftJoin(b).on(a.BOOK_ID.eq(b.ID))
            .where(b.LIBRARY_ID.`in`(it))
        }
      }
      .orderBy(a.ROLE)
      .fetch(a.ROLE)

  override fun findAllGenres(filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(g.GENRE)
      .from(g)
      .apply {
        filterOnLibraryIds?.let {
          leftJoin(s).on(g.SERIES_ID.eq(s.ID))
            .where(s.LIBRARY_ID.`in`(it))
        }
      }
      .orderBy(lower(g.GENRE))
      .fetchSet(g.GENRE)

  override fun findAllGenresByLibrary(libraryId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(g.GENRE)
      .from(g)
      .leftJoin(s).on(g.SERIES_ID.eq(s.ID))
      .where(s.LIBRARY_ID.eq(libraryId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(g.GENRE))
      .fetchSet(g.GENRE)

  override fun findAllGenresByCollection(collectionId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(g.GENRE)
      .from(g)
      .leftJoin(cs).on(g.SERIES_ID.eq(cs.SERIES_ID))
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(g.SERIES_ID.eq(s.ID)) } }
      .where(cs.COLLECTION_ID.eq(collectionId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(g.GENRE))
      .fetchSet(g.GENRE)

  override fun findAllSeriesAndBookTags(filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.select(bt.TAG.`as`("tag"))
      .from(bt)
      .apply {
        filterOnLibraryIds?.let {
          leftJoin(b).on(bt.BOOK_ID.eq(b.ID))
            .where(b.LIBRARY_ID.`in`(it))
        }
      }
      .union(
        select(st.TAG.`as`("tag"))
          .from(st)
          .apply {
            filterOnLibraryIds?.let {
              leftJoin(s).on(st.SERIES_ID.eq(s.ID))
                .where(s.LIBRARY_ID.`in`(it))
            }
          }
      )
      .fetchSet(0, String::class.java)
      .sortedBy { it.toLowerCase() }
      .toSet()

  override fun findAllSeriesTags(filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.select(st.TAG)
      .from(st)
      .apply {
        filterOnLibraryIds?.let {
          leftJoin(s).on(st.SERIES_ID.eq(s.ID))
            .where(s.LIBRARY_ID.`in`(it))
        }
      }
      .orderBy(lower(st.TAG))
      .fetchSet(st.TAG)

  override fun findAllSeriesTagsByLibrary(libraryId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.select(st.TAG)
      .from(st)
      .leftJoin(s).on(st.SERIES_ID.eq(s.ID))
      .where(s.LIBRARY_ID.eq(libraryId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(st.TAG))
      .fetchSet(st.TAG)

  override fun findAllBookTagsBySeries(seriesId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.select(bt.TAG)
      .from(bt)
      .leftJoin(b).on(bt.BOOK_ID.eq(b.ID))
      .where(b.SERIES_ID.eq(seriesId))
      .apply { filterOnLibraryIds?.let { and(b.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(bt.TAG))
      .fetchSet(bt.TAG)

  override fun findAllSeriesTagsByCollection(collectionId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.select(st.TAG)
      .from(st)
      .leftJoin(cs).on(st.SERIES_ID.eq(cs.SERIES_ID))
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(st.SERIES_ID.eq(s.ID)) } }
      .where(cs.COLLECTION_ID.eq(collectionId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(st.TAG))
      .fetchSet(st.TAG)

  override fun findAllBookTags(filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.select(bt.TAG)
      .from(bt)
      .apply {
        filterOnLibraryIds?.let {
          leftJoin(b).on(bt.BOOK_ID.eq(b.ID))
            .where(b.LIBRARY_ID.`in`(it))
        }
      }
      .orderBy(lower(st.TAG))
      .fetchSet(st.TAG)

  override fun findAllLanguages(filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(sd.LANGUAGE)
      .from(sd)
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(sd.SERIES_ID.eq(s.ID)) } }
      .where(sd.LANGUAGE.ne(""))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(sd.LANGUAGE)
      .fetchSet(sd.LANGUAGE)

  override fun findAllLanguagesByLibrary(libraryId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(sd.LANGUAGE)
      .from(sd)
      .leftJoin(s).on(sd.SERIES_ID.eq(s.ID))
      .where(sd.LANGUAGE.ne(""))
      .and(s.LIBRARY_ID.eq(libraryId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(sd.LANGUAGE)
      .fetchSet(sd.LANGUAGE)

  override fun findAllLanguagesByCollection(collectionId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(sd.LANGUAGE)
      .from(sd)
      .leftJoin(cs).on(sd.SERIES_ID.eq(cs.SERIES_ID))
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(sd.SERIES_ID.eq(s.ID)) } }
      .where(sd.LANGUAGE.ne(""))
      .and(cs.COLLECTION_ID.eq(collectionId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(sd.LANGUAGE)
      .fetchSet(sd.LANGUAGE)

  override fun findAllPublishers(filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(sd.PUBLISHER)
      .from(sd)
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(sd.SERIES_ID.eq(s.ID)) } }
      .where(sd.PUBLISHER.ne(""))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(sd.PUBLISHER))
      .fetchSet(sd.PUBLISHER)

  override fun findAllPublishersByLibrary(libraryId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(sd.PUBLISHER)
      .from(sd)
      .leftJoin(s).on(sd.SERIES_ID.eq(s.ID))
      .where(sd.PUBLISHER.ne(""))
      .and(s.LIBRARY_ID.eq(libraryId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(sd.PUBLISHER))
      .fetchSet(sd.PUBLISHER)

  override fun findAllPublishersByCollection(collectionId: String, filterOnLibraryIds: Collection<String>?): Set<String> =
    dsl.selectDistinct(sd.PUBLISHER)
      .from(sd)
      .leftJoin(cs).on(sd.SERIES_ID.eq(cs.SERIES_ID))
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(sd.SERIES_ID.eq(s.ID)) } }
      .where(sd.PUBLISHER.ne(""))
      .and(cs.COLLECTION_ID.eq(collectionId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(lower(sd.PUBLISHER))
      .fetchSet(sd.PUBLISHER)

  override fun findAllAgeRatings(filterOnLibraryIds: Collection<String>?): Set<Int> =
    dsl.selectDistinct(sd.AGE_RATING)
      .from(sd)
      .apply {
        filterOnLibraryIds?.let {
          leftJoin(s).on(sd.SERIES_ID.eq(s.ID))
            .where(s.LIBRARY_ID.`in`(it))
        }
      }
      .orderBy(sd.AGE_RATING)
      .fetchSet(sd.AGE_RATING)

  override fun findAllAgeRatingsByLibrary(libraryId: String, filterOnLibraryIds: Collection<String>?): Set<Int> =
    dsl.selectDistinct(sd.AGE_RATING)
      .from(sd)
      .leftJoin(s).on(sd.SERIES_ID.eq(s.ID))
      .where(s.LIBRARY_ID.eq(libraryId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(sd.AGE_RATING)
      .fetchSet(sd.AGE_RATING)

  override fun findAllAgeRatingsByCollection(collectionId: String, filterOnLibraryIds: Collection<String>?): Set<Int?> =
    dsl.selectDistinct(sd.AGE_RATING)
      .from(sd)
      .leftJoin(cs).on(sd.SERIES_ID.eq(cs.SERIES_ID))
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(sd.SERIES_ID.eq(s.ID)) } }
      .where(cs.COLLECTION_ID.eq(collectionId))
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(sd.AGE_RATING)
      .fetchSet(sd.AGE_RATING)

  override fun findAllSeriesReleaseDates(filterOnLibraryIds: Collection<String>?): Set<LocalDate> =
    dsl.selectDistinct(bma.RELEASE_DATE)
      .from(bma)
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(bma.SERIES_ID.eq(s.ID)) } }
      .where(bma.RELEASE_DATE.isNotNull)
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(bma.RELEASE_DATE.desc())
      .fetchSet(bma.RELEASE_DATE)

  override fun findAllSeriesReleaseDatesByLibrary(libraryId: String, filterOnLibraryIds: Collection<String>?): Set<LocalDate> =
    dsl.selectDistinct(bma.RELEASE_DATE)
      .from(bma)
      .leftJoin(s).on(bma.SERIES_ID.eq(s.ID))
      .where(s.LIBRARY_ID.eq(libraryId))
      .and(bma.RELEASE_DATE.isNotNull)
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(bma.RELEASE_DATE.desc())
      .fetchSet(bma.RELEASE_DATE)

  override fun findAllSeriesReleaseDatesByCollection(collectionId: String, filterOnLibraryIds: Collection<String>?): Set<LocalDate> =
    dsl.selectDistinct(bma.RELEASE_DATE)
      .from(bma)
      .leftJoin(cs).on(bma.SERIES_ID.eq(cs.SERIES_ID))
      .apply { filterOnLibraryIds?.let { leftJoin(s).on(bma.SERIES_ID.eq(s.ID)) } }
      .where(cs.COLLECTION_ID.eq(collectionId))
      .and(bma.RELEASE_DATE.isNotNull)
      .apply { filterOnLibraryIds?.let { and(s.LIBRARY_ID.`in`(it)) } }
      .orderBy(bma.RELEASE_DATE.desc())
      .fetchSet(bma.RELEASE_DATE)

  private fun BookMetadataAuthorRecord.toDomain(): Author =
    Author(
      name = name,
      role = role
    )

  private fun BookMetadataAggregationAuthorRecord.toDomain(): Author =
    Author(
      name = name,
      role = role
    )
}
