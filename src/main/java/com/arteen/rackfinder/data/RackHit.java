package com.arteen.rackfinder.data;

/**
 * One rack containing a given item, with the amount stored there.
 */
public record RackHit(RackRecord record, int count) {
}
