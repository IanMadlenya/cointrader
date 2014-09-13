package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Amount;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.OrderState;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.service.QuoteService;
import org.cryptocoinpartners.util.FeesUtil;
import org.slf4j.Logger;

/**
 * MockOrderService simulates the Filling of Orders by looking at broadcast Book data for price and volume information.
 *
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class MockOrderService extends BaseOrderService {

	@Override
	protected void handleSpecificOrder(SpecificOrder specificOrder) {
		if (specificOrder.getStopPrice() != null)
			reject(specificOrder, "Stop prices unsupported");
		if (specificOrder.getFillType() != FillType.STOP_LIMIT && specificOrder.getFillType() != FillType.TRAILING_STOP_LIMIT)
			PersitOrder(specificOrder);
		pendingOrders.add(specificOrder);
		updateOrderState(specificOrder, OrderState.PLACED);
	}

	@SuppressWarnings("ConstantConditions")
	@When("select * from Book")
	private void handleBook(Book b) {
		List<Fill> fills = new ArrayList<>();
		// todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
		for (SpecificOrder order : pendingOrders) {
			if (order.getMarket().equals(b.getMarket())) {
				if (order.isBid()) {
					long remainingVolume = order.getUnfilledVolumeCount();
					for (Offer ask : b.getAsks()) {
						if (order.getLimitPrice() != null && order.getLimitPrice().getCount() < ask.getPriceCount())
							break;
						long fillVolume = Math.min(Math.abs(ask.getVolumeCount()), remainingVolume);
						Fill fill = new Fill(order, ask.getTime(), ask.getMarket(), ask.getPriceCount(), fillVolume);
						fills.add(fill);
						remainingVolume -= fillVolume;
						logFill(order, ask, fill);
						if (remainingVolume == 0)
							break;
					}
				}
				if (order.isAsk()) {
					long remainingVolume = order.getUnfilledVolumeCount(); // this will be negative
					for (Offer bid : b.getBids()) {
						if (order.getLimitPrice() != null && order.getLimitPrice().getCount() > bid.getPriceCount())
							break;
						long fillVolume = -Math.min(bid.getVolumeCount(), Math.abs(remainingVolume));

						Fill fill = new Fill(order, bid.getTime(), bid.getMarket(), bid.getPriceCount(), fillVolume);

						fills.add(fill);
						remainingVolume -= fillVolume;
						logFill(order, bid, fill);
						if (remainingVolume == 0)
							break;
					}
				}
			}
		}
		for (Fill fill : fills) {
			Amount comission = FeesUtil.getExchangeFees(fill);
			fill.setCommission(comission);
			fill.getOrder().addFill(fill);
			context.publish(fill);
		}
	}

	@When("select * from OrderUpdate where state.open=false")
	private void completeOrder(OrderUpdate update) {
		//noinspection SuspiciousMethodCalls
		pendingOrders.remove(update.getOrder());
	}

	private void logFill(SpecificOrder order, Offer offer, Fill fill) {
		if (log.isDebugEnabled())
			log.debug("Mock fill of Order " + order + " with Offer " + offer + ": " + fill);
	}

	@Inject
	private Logger log;

	private final Collection<SpecificOrder> pendingOrders = new ArrayList<>();
	private QuoteService quotes;
}
