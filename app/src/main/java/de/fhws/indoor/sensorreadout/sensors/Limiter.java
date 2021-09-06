package de.fhws.indoor.sensorreadout.sensors;

///**
// * Created by Frank on 04.02.2016.
// */
//public class Limiter {
//
//	long last_ms = 0;
//	final int limit_ms;
//
//	/** ctor */
//	Limiter(final int limit_ms) {
//		this.limit_ms = limit_ms;
//	}
//
//	/** limit reached? */
//	boolean isOK() {
//		final long cur_ms = System.currentTimeMillis();
//		final long diff = cur_ms - last_ms;
//		final boolean ok = diff > limit_ms;
//		if (ok) {last_ms = cur_ms;}
//		return ok;
//	}
//
//}
