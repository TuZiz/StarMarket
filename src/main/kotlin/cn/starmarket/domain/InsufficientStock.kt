package cn.starmarket.domain

/** Expected rejection before any inventory reservation or external payment. */
class InsufficientStock(val requested: Long, val available: Long) :
    IllegalStateException("Insufficient market stock: requested=$requested, available=$available")
