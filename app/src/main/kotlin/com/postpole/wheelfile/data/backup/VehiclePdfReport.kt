package com.postpole.wheelfile.data.backup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.economy.VehicleStats
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.domain.report.EconomySeries
import com.postpole.wheelfile.domain.report.ReportBuilder
import com.postpole.wheelfile.domain.report.ReportWindow
import com.postpole.wheelfile.importer.ImportService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Everything the report prints. */
class PdfReportInput(
    val vehicle: Vehicle,
    val logs: List<EnergyLog>,
    val services: List<ServiceRecord>,
    val expenses: List<Expense>,
    val attachments: List<Attachment>,
    val stats: VehicleStats,
    val economy: List<EconomySeries>,
)

/** Visible text of the report, so it follows the app language. */
class PdfLabels(
    val title: String,
    val generated: (String) -> String,
    val details: String,
    val summary: String,
    val economy: String,
    val services: String,
    val expenses: String,
    val attachments: String,
    val totalCost: String,
    val energyCost: String,
    val serviceCost: String,
    val expenseCost: String,
    val odometer: String,
    val fillUps: String,
    val none: String,
    val page: (Int) -> String,
    val economyUnit: (com.postpole.wheelfile.data.db.EconomyUnit) -> String,
    val attachmentsCount: (Int) -> String,
)

/**
 * Pro: vehicle history / resale report drawn with [PdfDocument] on the device (spec §2.8). Vehicle details, a cost summary,
 * an economy chart, the service history and an attachments index. Shared through the share sheet; no ad is ever shown here.
 */
class VehiclePdfReport(private val labels: PdfLabels, private val locale: Locale = Locale.getDefault()) {

    /** Renders [input] to [out] and returns the page count. */
    fun render(input: PdfReportInput, out: OutputStream): Int {
        val doc = PdfDocument()
        val writer = PageWriter(doc, labels)
        val v = input.vehicle
        val money = NumberFormat.getCurrencyInstance(locale).apply {
            runCatching { currency = Currency.getInstance(v.currency) }
            val d = ImportService.fractionDigits(v.currency)
            minimumFractionDigits = d
            maximumFractionDigits = d
        }
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }

        writer.heading(labels.title, 22f)
        writer.line(v.name, bold = true, size = 16f)
        writer.line(labels.generated(date.format(Date())), size = 10f, color = Color.DKGRAY)
        writer.gap(10f)

        writer.heading(labels.details)
        val details = listOfNotNull(
            listOfNotNull(v.make, v.model, v.year?.toString()).joinToString(" ").ifBlank { null },
            v.plate?.takeIf { it.isNotBlank() },
            v.vin?.takeIf { it.isNotBlank() }?.let { "VIN $it" },
            input.stats.odometer?.let { "${labels.odometer}: ${number.format(it)} ${v.distanceUnit.name.lowercase()}" },
        )
        if (details.isEmpty()) writer.line(labels.none) else details.forEach { writer.line(it) }
        writer.gap(10f)

        writer.heading(labels.summary)
        val c = input.stats.costs
        writer.line("${labels.totalCost}: ${money.format(c.totalMinor.toMajor(v.currency))}", bold = true)
        writer.line("${labels.energyCost}: ${money.format(c.energyMinor.toMajor(v.currency))}")
        writer.line("${labels.serviceCost}: ${money.format(c.serviceMinor.toMajor(v.currency))}")
        writer.line("${labels.expenseCost}: ${money.format(c.expensesMinor.toMajor(v.currency))}")
        writer.line("${labels.fillUps}: ${input.logs.size}")
        input.stats.streams.forEach { s ->
            s.average?.let { writer.line("${number.format(it)} ${labels.economyUnit(s.unit)}") }
        }
        writer.gap(10f)

        val series = input.economy.firstOrNull { it.points.size >= 2 }
        if (series != null) {
            writer.heading(labels.economy)
            writer.chart(series.points.map { it.dateTime.toDouble() to it.value })
            writer.line(labels.economyUnit(series.unit), size = 10f, color = Color.DKGRAY)
            writer.gap(10f)
        }

        writer.heading(labels.services)
        if (input.services.isEmpty()) writer.line(labels.none)
        input.services.sortedByDescending { it.dateTime }.forEach { s ->
            val odo = s.odometer?.let { " · ${number.format(it)} ${v.distanceUnit.name.lowercase()}" }.orEmpty()
            val cost = s.totalMinor?.let { " · ${money.format(it.toMajor(v.currency))}" }.orEmpty()
            writer.line("${date.format(Date(s.dateTime))}$odo$cost")
            writer.line("   " + s.items.joinToString(", ").ifBlank { s.workshop.orEmpty() }, size = 10f, color = Color.DKGRAY)
        }
        writer.gap(10f)

        writer.heading(labels.expenses)
        if (input.expenses.isEmpty()) writer.line(labels.none)
        input.expenses.sortedByDescending { it.dateTime }.forEach { e ->
            writer.line(
                "${date.format(Date(e.dateTime))} · ${e.category.name.lowercase().replace('_', ' ')} · " +
                    money.format(e.amountMinor.toMajor(v.currency)),
            )
        }
        writer.gap(10f)

        writer.heading(labels.attachments)
        val counts = OwnerType.entries.associateWith { t -> input.attachments.count { it.ownerType == t } }.filterValues { it > 0 }
        if (counts.isEmpty()) writer.line(labels.none) else writer.line(labels.attachmentsCount(counts.values.sum()))

        val pages = writer.finish()
        doc.writeTo(out)
        doc.close()
        out.flush()
        return pages
    }

    private fun Long.toMajor(currency: String): Double = this / Math.pow(10.0, ImportService.fractionDigits(currency).toDouble())

    private class PageWriter(private val doc: PdfDocument, private val labels: PdfLabels) {
        private val width = 595
        private val height = 842
        private val margin = 40f
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun ensure(space: Float) {
            if (page == null || y + space > height - margin - 20f) {
                newPage()
            }
        }

        private fun newPage() {
            closePage()
            pageNumber++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
            page = p
            canvas = p.canvas
            y = margin
        }

        private fun closePage() {
            val p = page ?: return
            paint.reset()
            paint.isAntiAlias = true
            paint.textSize = 9f
            paint.color = Color.GRAY
            canvas?.drawText(labels.page(pageNumber), margin, height - margin / 2, paint)
            doc.finishPage(p)
            page = null
            canvas = null
        }

        fun heading(text: String, size: Float = 14f) {
            ensure(size + 12f)
            paint.reset()
            paint.isAntiAlias = true
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = size
            paint.color = Color.rgb(0x1B, 0x3A, 0x57)
            y += size
            canvas!!.drawText(text, margin, y, paint)
            y += 6f
        }

        fun line(text: String, bold: Boolean = false, size: Float = 11f, color: Int = Color.BLACK) {
            ensure(size + 6f)
            paint.reset()
            paint.isAntiAlias = true
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize = size
            paint.color = color
            y += size + 3f
            // Long lines are cut to the page width instead of running off it.
            val fitted = paint.breakText(text, true, width - 2 * margin, null).let { if (it < text.length) text.take(it) else text }
            canvas!!.drawText(fitted, margin, y, paint)
        }

        fun gap(h: Float) {
            y += h
        }

        /** A line chart of (x, y) points in a 515 x 110 box. */
        fun chart(points: List<Pair<Double, Double>>) {
            val h = 110f
            ensure(h + 10f)
            val w = width - 2 * margin
            val left = margin
            val top = y + 6f
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.LTGRAY
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas!!.drawRect(left, top, left + w, top + h, paint)
            val minX = points.minOf { it.first }
            val maxX = points.maxOf { it.first }
            val minY = points.minOf { it.second }
            val maxY = points.maxOf { it.second }
            val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
            val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0
            val path = Path()
            points.forEachIndexed { i, (px, py) ->
                val sx = left + ((px - minX) / spanX * (w - 12f)).toFloat() + 6f
                val sy = top + h - 6f - ((py - minY) / spanY * (h - 12f)).toFloat()
                if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            }
            paint.color = Color.rgb(0x1B, 0x6F, 0xB0)
            paint.strokeWidth = 1.8f
            canvas!!.drawPath(path, paint)
            y = top + h + 4f
        }

        /** Closes the last page (starting one if nothing was written) and returns the page count. */
        fun finish(): Int {
            if (page == null && pageNumber == 0) newPage()
            closePage()
            return pageNumber
        }
    }
}

/** Loads one vehicle's data and renders its report. Pro only; the caller checks the entitlement. */
class VehiclePdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    /** Returns the page count, or null when the vehicle no longer exists. */
    suspend fun export(vehicleId: Long, out: OutputStream): Int? = withContext(Dispatchers.IO) {
        val vehicle = db.vehicleDao().get(vehicleId) ?: return@withContext null
        val logs = db.energyLogDao().getAll(vehicleId)
        val services = db.serviceRecordDao().getForVehicle(vehicleId)
        val expenses = db.expenseDao().getForVehicle(vehicleId)
        val ownerIds = logs.map { it.id }.toSet() + services.map { it.id } + expenses.map { it.id }
        val attachments = db.attachmentDao().getAll().filter { it.ownerType != OwnerType.DOCUMENT && it.ownerId in ownerIds }
        val input = PdfReportInput(
            vehicle, logs, services, expenses, attachments,
            VehicleStatsCalculator.compute(vehicle, logs, services, expenses),
            ReportBuilder.economySeries(vehicle, logs, ReportWindow(null, null)),
        )
        VehiclePdfReport(labels()).render(input, out)
    }

    private fun labels() = PdfLabels(
        title = context.getString(R.string.pdf_title),
        generated = { context.getString(R.string.pdf_generated, it) },
        details = context.getString(R.string.pdf_section_details),
        summary = context.getString(R.string.pdf_section_summary),
        economy = context.getString(R.string.pdf_section_economy),
        services = context.getString(R.string.pdf_section_services),
        expenses = context.getString(R.string.pdf_section_expenses),
        attachments = context.getString(R.string.pdf_section_attachments),
        totalCost = context.getString(R.string.pdf_total_cost),
        energyCost = context.getString(R.string.pdf_energy_cost),
        serviceCost = context.getString(R.string.pdf_service_cost),
        expenseCost = context.getString(R.string.pdf_expense_cost),
        odometer = context.getString(R.string.pdf_odometer),
        fillUps = context.getString(R.string.pdf_fill_ups),
        none = context.getString(R.string.pdf_none),
        page = { context.getString(R.string.pdf_page, it) },
        economyUnit = { context.getString(economyUnitLabel(it)) },
        attachmentsCount = { context.resources.getQuantityString(R.plurals.pdf_attachments_count, it, it) },
    )

    private fun economyUnitLabel(unit: com.postpole.wheelfile.data.db.EconomyUnit): Int = when (unit) {
        com.postpole.wheelfile.data.db.EconomyUnit.L_PER_100KM -> R.string.pdf_unit_l_per_100km
        com.postpole.wheelfile.data.db.EconomyUnit.KM_PER_L -> R.string.pdf_unit_km_per_l
        com.postpole.wheelfile.data.db.EconomyUnit.MPG_US -> R.string.pdf_unit_mpg_us
        com.postpole.wheelfile.data.db.EconomyUnit.MPG_UK -> R.string.pdf_unit_mpg_uk
        com.postpole.wheelfile.data.db.EconomyUnit.KWH_PER_100KM -> R.string.pdf_unit_kwh_per_100km
        com.postpole.wheelfile.data.db.EconomyUnit.MI_PER_KWH -> R.string.pdf_unit_mi_per_kwh
        com.postpole.wheelfile.data.db.EconomyUnit.KWH_PER_100MI -> R.string.pdf_unit_kwh_per_100mi
        com.postpole.wheelfile.data.db.EconomyUnit.KG_PER_100KM -> R.string.pdf_unit_kg_per_100km
    }
}
