package com.alvinb.counterpos.data.local.entity

import org.json.JSONArray
import org.json.JSONObject

data class SaleUnit(
    val name: String,
    val priceKsh: Int,
    /** Whole stock units consumed per sold unit. Defaults to one for legacy catalog rows. */
    val stockUnitsPerSale: Int = 1
)

fun parseSaleUnits(json: String?, fallbackPrice: Int = 0): List<SaleUnit> {
    if (json.isNullOrBlank()) {
        return listOf(SaleUnit(name = "Bottle", priceKsh = fallbackPrice))
    }
    return try {
        val array = JSONArray(json)
        val list = mutableListOf<SaleUnit>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name", "Bottle").ifBlank { "Bottle" }
            val price = obj.optInt("priceKsh", fallbackPrice)
            val stockUnits = obj.optInt("stockUnitsPerSale", 1).coerceAtLeast(1)
            list.add(SaleUnit(name = name, priceKsh = price, stockUnitsPerSale = stockUnits))
        }
        if (list.isEmpty()) listOf(SaleUnit(name = "Bottle", priceKsh = fallbackPrice)) else list
    } catch (_: Exception) {
        listOf(SaleUnit(name = "Bottle", priceKsh = fallbackPrice))
    }
}

fun serializeSaleUnits(units: List<SaleUnit>): String {
    val array = JSONArray()
    for (unit in units) {
        if (unit.name.isNotBlank()) {
            val obj = JSONObject()
            obj.put("name", unit.name.trim())
            obj.put("priceKsh", unit.priceKsh.coerceAtLeast(0))
            obj.put("stockUnitsPerSale", unit.stockUnitsPerSale.coerceAtLeast(1))
            array.put(obj)
        }
    }
    return array.toString()
}
