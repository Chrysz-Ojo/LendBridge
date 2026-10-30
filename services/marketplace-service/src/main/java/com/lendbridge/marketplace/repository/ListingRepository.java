package com.lendbridge.marketplace.repository;

import com.lendbridge.marketplace.model.Listing;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

  /** Nullable-parameter pattern: each filter only applies when its argument is non-null, so a
   * caller can supply any subset of the optional range/purpose filters (PRD E3-S2). Only
   * {@code ACTIVE} listings are ever browsable — a {@code DELISTED} row is kept, not deleted, as a
   * record that the consumer saw the cancellation. */
  @Query(
      "SELECT l FROM Listing l WHERE l.status = com.lendbridge.marketplace.model.ListingStatus.ACTIVE "
          + "AND (:minAmountKobo IS NULL OR l.amountKobo >= :minAmountKobo) "
          + "AND (:maxAmountKobo IS NULL OR l.amountKobo <= :maxAmountKobo) "
          + "AND (:minRateBps IS NULL OR l.interestRateBps >= :minRateBps) "
          + "AND (:maxRateBps IS NULL OR l.interestRateBps <= :maxRateBps) "
          + "AND (:minTenorMonths IS NULL OR l.tenorMonths >= :minTenorMonths) "
          + "AND (:maxTenorMonths IS NULL OR l.tenorMonths <= :maxTenorMonths) "
          + "AND (:purpose IS NULL OR l.purpose = :purpose) "
          + "ORDER BY l.listedAt DESC")
  List<Listing> search(
      @Param("minAmountKobo") Long minAmountKobo,
      @Param("maxAmountKobo") Long maxAmountKobo,
      @Param("minRateBps") Integer minRateBps,
      @Param("maxRateBps") Integer maxRateBps,
      @Param("minTenorMonths") Integer minTenorMonths,
      @Param("maxTenorMonths") Integer maxTenorMonths,
      @Param("purpose") String purpose);
}
