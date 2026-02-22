package fun.fengwk.mmh.core.service.scrape;

import fun.fengwk.mmh.core.service.scrape.model.ScrapeRequest;
import fun.fengwk.mmh.core.service.scrape.model.ScrapeResponse;

/**
 * Scrape service entry.
 *
 * @author fengwk
 */
public interface PageScrapeService {

    ScrapeResponse scrape(ScrapeRequest request);

}
