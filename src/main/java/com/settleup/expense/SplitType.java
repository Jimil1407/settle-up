package com.settleup.expense;

/** How an expense total is divided among its participants. */
public enum SplitType {
    /** Equal shares, with leftover paise distributed by the largest-remainder method. */
    EQUAL,
    /** Proportional to integer weights, e.g. a couple counting as 2 units. */
    WEIGHTED,
    /** Caller supplies every share; the only rule is that they sum to the total. */
    EXACT
}
