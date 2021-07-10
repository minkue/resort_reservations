package resortreservation;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="resorts", path="resorts")
public interface ResortRepository extends PagingAndSortingRepository<Resort, Long>{

}
