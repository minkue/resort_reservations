package resortreservation;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="vouchers", path="vouchers")
public interface VoucherRepository extends PagingAndSortingRepository<Voucher, Long>{


}
