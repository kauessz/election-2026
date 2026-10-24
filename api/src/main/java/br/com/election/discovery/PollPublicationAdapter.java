package br.com.election.discovery;

import java.util.Optional;
/** Future adapters may propose staging content only. No import or persistence capability here. */
public interface PollPublicationAdapter {
    Optional<DiscoveryModels.Review> propose(DiscoveryModels.Discovery discovery);
}
