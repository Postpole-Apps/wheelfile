package com.postpole.wheelfile.domain.vehicle

/** Free-tier vehicle cap (spec §5.1): 2 active vehicles. Archived vehicles do not count. */
object VehicleLimit {
    const val FREE_ACTIVE_VEHICLES = 2

    /** True when one more active vehicle may be added (or an archived one restored). */
    fun canAddActive(activeCount: Int, isPro: Boolean): Boolean = isPro || activeCount < FREE_ACTIVE_VEHICLES
}
