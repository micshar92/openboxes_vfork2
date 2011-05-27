package org.pih.warehouse.order

import java.util.List;

import org.apache.commons.collections.FactoryUtils;
import org.apache.commons.collections.list.LazyList;
import org.pih.warehouse.core.Person;
import org.pih.warehouse.shipping.Shipment;
import org.pih.warehouse.shipping.ShipmentItem;

class OrderCommand {

	Person recipient
	Date deliveredOn
	Order order
	Shipment shipment	
	
	// Not the actual order items, but rather all the line items on the receive order page.  
	// This means that we might have more than one OrderItemCommand per OrderItem.
	List<OrderItemCommand> orderItems =
		LazyList.decorate(new ArrayList(), FactoryUtils.instantiateFactory(OrderItemCommand.class));
	
	static constraints = {

	}
	
}

