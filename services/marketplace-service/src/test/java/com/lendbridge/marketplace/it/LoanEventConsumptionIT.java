package com.lendbridge.marketplace.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.loan.avro.LoanDelisted;
import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.marketplace.model.Listing;
import com.lendbridge.marketplace.model.ListingStatus;
import com.lendbridge.marketplace.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real {@code @KafkaListener} wiring end-to-end (topic names, Avro deserialization
 * against the schema registry, transaction commit) — the unit tests in {@code messaging/} already
 * cover the consumer logic in isolation, calling the method directly; this is the one place that
 * actually proves a message produced on the wire gets consumed and reflected through the public
 * API, same as a lender or the next service in the chain would see it. No Awaitility in this repo
 * (would be a new dependency), so a plain poll-with-timeout loop stands in for it.
 */
class LoanEventConsumptionIT extends AbstractIntegrationTest {

  @Test
  void loanListed_thenLoanDelisted_listingAppearsThenDisappears() throws InterruptedException {
    UUID loanId = UUID.randomUUID();
    LoanListed listed =
        LoanListed.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(300_000L)
            .setInterestRateBps(1800)
            .setTenorMonths(6)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    kafkaTemplate.send(new ProducerRecord<>("loan.listed", loanId.toString(), listed));

    Listing afterListed =
        pollUntilPresent(() -> listingRepository.findById(loanId).filter(l -> l.getStatus() == ListingStatus.ACTIVE));
    assertThat(afterListed.getAmountKobo()).isEqualTo(300_000L);

    LoanDelisted delisted =
        LoanDelisted.newBuilder().setLoanId(loanId.toString()).setOccurredAtEpochMillis(System.currentTimeMillis()).build();
    kafkaTemplate.send(new ProducerRecord<>("loan.delisted", loanId.toString(), delisted));

    Listing afterDelisted =
        pollUntilPresent(() -> listingRepository.findById(loanId).filter(l -> l.getStatus() == ListingStatus.DELISTED));
    assertThat(afterDelisted.getStatus()).isEqualTo(ListingStatus.DELISTED);
  }

  private Listing pollUntilPresent(Supplier<Optional<Listing>> query) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (Instant.now().isBefore(deadline)) {
      Optional<Listing> result = query.get();
      if (result.isPresent()) {
        return result.get();
      }
      Thread.sleep(250);
    }
    throw new AssertionError("Condition not met within 15 seconds");
  }
}
