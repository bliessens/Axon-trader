package org.axonframework.samples.trader.app.command.trading;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.samples.trader.app.api.order.BuyOrderPlacedEvent;
import org.axonframework.samples.trader.app.api.order.OrderBookCreatedEvent;
import org.axonframework.samples.trader.app.api.order.SellOrderPlacedEvent;
import org.axonframework.samples.trader.app.api.order.TradeExecutedEvent;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * @author Allard Buijze
 */
class OrderBook extends AbstractAnnotatedAggregateRoot {

    private SortedSet<Order> buyOrders = new TreeSet<Order>(new OrderComparator());
    private SortedSet<Order> sellOrders = new TreeSet<Order>(new OrderComparator());

    public OrderBook(UUID identifier, UUID tradeItemIdentifier) {
        super(identifier);
        apply(new OrderBookCreatedEvent(tradeItemIdentifier));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public OrderBook(UUID identifier) {
        super(identifier);
    }


    public void addBuyOrder(UUID orderId, long tradeCount, int itemPrice, UUID userId) {
        apply(new BuyOrderPlacedEvent(orderId, tradeCount, itemPrice, userId));
        executeTrades();
    }

    public void addSellOrder(UUID orderId, long tradeCount, int itemPrice, UUID userId) {
        apply(new SellOrderPlacedEvent(orderId, tradeCount, itemPrice, userId));
        executeTrades();
    }

    private void executeTrades() {
        boolean tradingDone = false;
        while (!tradingDone && !buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order highestBuyer = buyOrders.last();
            Order lowestSeller = sellOrders.first();
            if (highestBuyer.getItemPrice() >= lowestSeller.getItemPrice()) {
                long matchedTradeCount = Math.min(highestBuyer.getItemsRemaining(), lowestSeller.getItemsRemaining());
                int matchedTradePrice = ((highestBuyer.getItemPrice() + lowestSeller.getItemPrice()) / 2);
                apply(new TradeExecutedEvent(matchedTradeCount,
                                             matchedTradePrice,
                                             highestBuyer.getOrderId(),
                                             lowestSeller.getOrderId()));
            } else {
                tradingDone = true;
            }
        }
    }

    @EventHandler
    protected void onBuyPlaced(BuyOrderPlacedEvent event) {
        buyOrders.add(new Order(event.getOrderId(), event.getItemPrice(), event.getTradeCount(), event.getUserId()));
    }

    @EventHandler
    protected void onSellPlaced(SellOrderPlacedEvent event) {
        sellOrders.add(new Order(event.getOrderId(), event.getItemPrice(), event.getTradeCount(), event.getUserId()));
    }

    @EventHandler
    protected void onTradeExecuted(TradeExecutedEvent event) {
        Order highestBuyer = buyOrders.last();
        Order lowestSeller = sellOrders.first();
        highestBuyer.recordTraded(event.getTradeCount());
        lowestSeller.recordTraded(event.getTradeCount());
        if (highestBuyer.getItemsRemaining() <= 0) {
            buyOrders.remove(highestBuyer);
        }
        if (lowestSeller.getItemsRemaining() <= 0) {
            sellOrders.remove(lowestSeller);
        }
    }

    @EventHandler
    protected void onOrderBookCreated(OrderBookCreatedEvent event) {
        // Nothing for now
    }

    private static class OrderComparator implements Comparator<Order> {

        public int compare(Order o1, Order o2) {
            return o1.getItemPrice() - o2.getItemPrice();
        }
    }
}
