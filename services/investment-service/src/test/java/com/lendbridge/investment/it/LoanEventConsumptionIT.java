package com.lendbridge.investment.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.investment.model.LoanListing;
import com.lendbridge.investment.support.AbstractIntegrationTest;
import com.lendbridge.loan.avro.LoanDelisted;
import com.lendbridge.loan.avro.LoanListed;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

/** Exercises the real {@code @KafkaListener} wiring end-to-end — same reasoning as
 * marketplace-service's own {@code LoanEventConsumptionIT}: the unit tests in {@code messaging/}
 * already cover the consumer logic directly, this proves a message produced on the wire actually
 * updates this service's own {@code LoanListing} projection. No Awaitility in this repo, so a
 * plain poll-with-timeout loop stands in for it. */
class LoanEventConsumptionIT extends AbstractIntegrationTest {

  @Test
  void loanListed_thenLoanDelisted_projectionAppearsThenGoesInactive() throws InterruptedException {
    UUID loanId = UUID.randomUUID();
    LoanListed listed =
        LoanListed.newBuilder()
            .setLoanId(loanId.toString())
            .setBorrowerUserId(UUID.randomUUID().toString())
            .setAmountKobo(1_000_000L)
            .setInterestRateBps(1800)
            .setTenorMonths(6)
            .setPurpose("BUSINESS")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    kafkaTemplate.send(new ProducerRecord<>("loan.listed", loanId.toString(), listed));

    LoanListing afterListed = pollUntilPresent(() -> loanListingRepository.findById(loanId).filter(LoanListing::isActive));
    assertThat(afterListed.getAmountKobo()).isEqualTo(1_000_000L);

    LoanDelisted delisted =
        LoanDelisted.newBuilder().setLoanId(loanId.toString()).setOccurredAtEpochMillis(System.currentTimeMillis()).build();
    kafkaTemplate.send(new ProducerRecord<>("loan.delisted", loanId.toString(), delisted));

    LoanListing afterDelisted =
        pollUntilPresent(() -> loanListingRepository.findById(loanId).filter(l -> !l.isActive()));
    assertThat(afterDelisted.isActive()).isFalse();
  }

  private LoanListing pollUntilPresent(Supplier<Optional<LoanListing>> query) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (Instant.now().isBefore(deadline)) {
      Optional<LoanListing> result = query.get();
      if (result.isPresent()) {
        return result.get();
      }
      Thread.sleep(250);
    }
    throw new AssertionError("Condition not met within 15 seconds");
  }
}
