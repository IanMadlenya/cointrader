package org.cryptocoinpartners.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;

import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.cryptocoinpartners.enumeration.FillType;
import org.joda.time.Instant;

/**
 * This is the base class for GeneralOrder and SpecificOrder.  To create orders, see OrderBuilder or BaseStrategy.order
 *
 * @author Mike Olson
 * @author Tim Olson
 */
@Entity
@Table(name = "\"Order\"")
// This is required because ORDER is a SQL keyword and must be escaped
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@SuppressWarnings({ "JpaDataSourceORMInspection", "UnusedDeclaration" })
public abstract class Order extends Event {

	public void setParentOrder(GeneralOrder parentOrder) {
		this.parentOrder = parentOrder;
	}

	@ManyToOne
	public GeneralOrder getParentOrder() {
		return parentOrder;
	}

	@Transient
	public abstract boolean isBid();

	@Transient
	public boolean isAsk() {
		return !isBid();
	}

	@Transient
	public Portfolio getPortfolio() {
		return portfolio;
	}

	//	public enum FillType {
	//		GOOD_TIL_CANCELLED, // Order stays open until explicitly cancelled or expired
	//		GTC_OR_MARGIN_CAP, // Order stays open until explicitly cancelled, expired, or the order is filled to the capacity of the currently available Positions
	//		CANCEL_REMAINDER, // This will cancel any remaining volume after a partial fill
	//		LIMIT, // Order stays open until quanity is filled at limit price or better
	//		STOP_LIMIT, // This will trigger a limit order at the specficed stop price
	//		TRAILING_STOP_LIMIT, // // This will trigger a limit order at the specficed stop price and will move the stop price by the trailing stop ammount as the price changes
	//
	//	}

	public FillType getFillType() {
		return fillType;
	}

	public String getComment() {
		return comment;
	}

	public enum MarginType {
		USE_MARGIN, // trade up to the limit of credit in the quote fungible
		CASH_ONLY, // do not trade more than the available cash-on-hand (quote fungible)
	}

	public MarginType getMarginType() {
		return marginType;
	}

	public Instant getExpiration() {
		return expiration;
	}

	public boolean getPanicForce() {
		return force;
	}

	public boolean setPanicForce() {
		return force;
	}

	public boolean isEmulation() {
		return emulation;
	}

	@OneToMany
	public Collection<Fill> getFills() {
		if (fills == null)
			fills = new ArrayList<>();
		return fills;
	}

	public void addFill(Fill fill) {
		getFills().add(fill);
	}

	@Transient
	public boolean hasFills() {
		return !getFills().isEmpty();
	}

	@Transient
	public abstract boolean isFilled();

	public DecimalAmount averageFillPrice() {
		if (!hasFills())
			return null;
		BigDecimal sumProduct = BigDecimal.ZERO;
		BigDecimal volume = BigDecimal.ZERO;
		Collection<Fill> fills = getFills();
		for (Fill fill : fills) {
			BigDecimal priceBd = fill.getPrice().asBigDecimal();
			BigDecimal volumeBd = fill.getVolume().asBigDecimal();
			sumProduct = sumProduct.add(priceBd.multiply(volumeBd));
			volume = volume.add(volumeBd);
		}
		return new DecimalAmount(sumProduct.divide(volume, Amount.mc));
	}

	protected Order(Instant time) {
		super(time);

	}

	protected void setFills(Collection<Fill> fills) {
		this.fills = fills;
	}

	protected void setFillType(FillType fillType) {
		this.fillType = fillType;
	}

	protected void setComment(String comment) {
		this.comment = comment;
	}

	protected void setMarginType(MarginType marginType) {
		this.marginType = marginType;
	}

	protected void setExpiration(Instant expiration) {
		this.expiration = expiration;
	}

	protected void setPanicForce(boolean force) {
		this.force = force;
	}

	protected void setEmulation(boolean emulation) {
		this.emulation = emulation;
	}

	protected void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;
	}

	private Portfolio portfolio;
	private Collection<Fill> fills;
	private FillType fillType;
	private MarginType marginType;
	private String comment;
	private Instant expiration;
	private boolean force; // allow this order to override various types of panic
	private boolean emulation; // ("allow order type emulation" [default, true] or "only use exchange's native functionality")
	protected GeneralOrder parentOrder;
}
