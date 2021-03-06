package weblog;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import weblog.model.Location;

@Repository
public interface LocationRepository extends CrudRepository<Location, Long> {
	Iterable<Location> findByIdGreaterThan(Long id);
	Iterable<Location> findByIdGreaterThanAndLogbookId(Long id, Long logbook_id);
}