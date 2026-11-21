package com.postpole.wheelfile.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.postpole.wheelfile.data.db.Attachment
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.domain.economy.VehicleStats
import com.postpole.wheelfile.domain.report.ReportBuilder
import com.postpole.wheelfile.domain.report.ReportWindow
import com.postpole.wheelfile.ui.common.formatMoney
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Everything one vehicle's report prints. */
data class VehicleReportData(
    val vehicle: Vehicle,
    val logs: List<EnergyLog>,
    val services: List<ServiceRecord>,
    val expenses: List<Expense>,
    val documents: List<VehicleDocument>,
    val attachments: List<Attachment>,
    val stats: VehicleStats,
)

/** Localised text for the report. The builder never reads Android resources itself. */
data class PdfLabels(
    val title: String,
    val generated: (String) -> String,
    val vehicleSection: String,
    val plate: String,
    val vin: String,
    val makeModelYear: String,
    val odometer: String,
    val unitsLine: String,
    val costsSection: String,
    val fuelOrEnergy: String,
    val service: String,
    val expenses: String,
    val total: String,
    val costPerDistance: (String) -> String,
    val economySection: String,
    val economyAverage: String,
    val economyEmpty: String,
    val servicesSection: String,
    val servicesEmpty: String,
    val colDate: String,
    val colOdometer: String,
    val colItems: String,
    val colCost: String,
    val attachmentsSection: String,
    val attachmentsLine: (title: String, count: Int) -> String,
    val footer: (page: Int) -> String,
    val economyUnit: (EconomyUnit) -> String,
    val distanceUnit: String,
    val serviceFallback: String,
)

/** Where the report's pages are drawn. [AndroidPdfSink] is the real one; unit tests count pages with a fake. */
interface PageSink {
    fun startPage(width: Int, height: Int, number: Int): Canvas
    fun finishPage()
    fun writeTo(out: OutputStream)
    fun close()
}

class AndroidPdfSink : PageSink {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null

    override fun startPage(width: Int, height: Int, number: Int): Canvas {
        val p = doc.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())
        page = p
        return p.canvas
    }

    override fun finishPage() {
        page?.let(doc::finishPage)
        page = null
    }

    override fun writeTo(out: OutputStream) = doc.writeTo(out)

    override fun close() = doc.close()
}

/**
 * Pro: the vehicle history / resale report (spec §2.8) drawn with [PdfDocument] on the device: vehicle details,
 * costs summary, economy chart, service history and an optional attachments index. A4, no ads, nothing leaves the phone
 * until the user shares the file.
 */
class PdfReportBuilder(
    private val labels: PdfLabels,
    private val locale: Locale = Locale.getDefault(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    private val sinkFactory: () -> PageSink = ::AndroidPdfSink,
) {
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    private val titlePaint = paint(20f, bold = true)
    private val h2Paint = paint(13f, bold = true)
    private val bodyPaint = paint(10f)
    private val headerPaint = paint(10f, bold = true)
    private val mutedPaint = paint(9f, color = Color.DKGRAY)
    private val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f }
    private val chartPaint = Paint().apply {
        color = Color.rgb(0, 96, 100); strokeWidth = 1.8f; style = Paint.Style.STROKE; isAntiAlias = true
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Writes the PDF to [out] and returns the page count. [includeAttachmentsIndex] adds the attachments section. */
    fun write(data: VehicleReportData, out: OutputStream, includeAttachmentsIndex: Boolean = true): Int {
        val sink = sinkFactory()
        try {
            val pager = Pager(sink)
            draw(pager, data, includeAttachmentsIndex)
            pager.finish()
            sink.writeTo(out)
            return pager.pageCount
        } finally {
            sink.close()
        }
    }

    private fun draw(p: Pager, d: VehicleReportData, includeAttachments: Boolean) {
        val v = d.vehicle
        p.text(labels.title, titlePaint, gap = 6f)
        p.text(v.name, h2Paint)
        p.text(labels.generated(date(now())), mutedPaint, gap = 10f)

        p.section(labels.vehicleSection, h2Paint)
        val makeModel = listOfNotNull(v.make, v.model, v.year?.toString()).joinToString(" ")
        if (makeModel.isNotBlank()) p.text("${labels.makeModelYear}: $makeModel", bodyPaint)
        v.plate?.let { p.text("${labels.plate}: $it", bodyPaint) }
        v.vin?.let { p.text("${labels.vin}: $it", bodyPaint) }
        d.stats.odometer?.let { p.text("${labels.odometer}: ${number(it, 0)} ${labels.distanceUnit}", bodyPaint) }
        p.text(labels.unitsLine, bodyPaint, gap = 8f)

        p.section(labels.costsSection, h2Paint)
        val c = d.stats.costs
        fun money(minor: Long) = formatMoney(minor, v.currency, locale)
        p.row(labels.fuelOrEnergy, money(c.energyMinor), bodyPaint)
        p.row(labels.service, money(c.serviceMinor), bodyPaint)
        p.row(labels.expenses, money(c.expensesMinor), bodyPaint)
        p.row(labels.total, money(c.totalMinor), h2Paint)
        c.costPerDistanceMinor?.let { p.text(labels.costPerDistance(money(it.toLong())), mutedPaint) }
        p.gap(8f)

        p.section(labels.economySection, h2Paint)
        val series = ReportBuilder.economySeries(v, d.logs, ReportWindow(null, null)).filter { it.points.size >= 2 }
        d.stats.streams.filter { it.average != null }.forEach {
            p.text("${labels.economyAverage}: ${number(it.average!!, 1)} ${labels.economyUnit(it.unit)}", bodyPaint)
        }
        if (series.isEmpty()) {
            p.text(labels.economyEmpty, mutedPaint, gap = 8f)
        } else {
            series.forEach { s -> chart(p, s.points.map { it.dateTime to it.value }, labels.economyUnit(s.unit)) }
        }

        p.section(labels.servicesSection, h2Paint)
        if (d.services.isEmpty()) {
            p.text(labels.servicesEmpty, mutedPaint)
        } else {
            p.table(
                listOf(labels.colDate, labels.colOdometer, labels.colItems, labels.colCost),
                d.services.sortedBy { it.dateTime }.map { s ->
                    listOf(
                        date(s.dateTime), s.odometer?.let { number(it, 0) } ?: "",
                        s.items.joinToString(", ").ifBlank { labels.serviceFallback },
                        s.totalMinor?.let(::money) ?: "",
                    )
                },
            )
        }

        if (includeAttachments) {
            val rows = attachmentRows(d)
            if (rows.isNotEmpty()) {
                p.gap(8f)
                p.section(labels.attachmentsSection, h2Paint)
                rows.forEach { (title, n) -> p.text(labels.attachmentsLine(title, n), bodyPaint) }
            }
        }
    }

    private fun attachmentRows(d: VehicleReportData): List<Pair<String, Int>> {
        fun count(type: OwnerType, id: Long) = d.attachments.count { it.ownerType == type && it.ownerId == id }
        val services = d.services.map { "${date(it.dateTime)} ${it.items.firstOrNull() ?: labels.serviceFallback}" to count(OwnerType.SERVICE, it.id) }
        val docs = d.documents.map { it.title to count(OwnerType.DOCUMENT, it.id) }
        return (services + docs).filter { it.second > 0 }
    }

    private fun chart(p: Pager, points: List<Pair<Long, Double>>, unit: String) {
        val height = 130f
        p.ensure(height + 34f)
        val left = p.left + 36f
        val box = RectF(left, p.y + 6f, p.right - 6f, p.y + 6f + height - 24f)
        val canvas = p.canvas
        canvas.drawLine(box.left, box.bottom, box.right, box.bottom, linePaint)
        canvas.drawLine(box.left, box.top, box.left, box.bottom, linePaint)
        val minV = points.minOf { it.second }
        val maxV = points.maxOf { it.second }
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val t0 = points.first().first
        val tSpan = (points.last().first - t0).takeIf { it > 0 } ?: 1L
        val path = Path()
        points.forEachIndexed { i, (t, value) ->
            val x = box.left + (t - t0).toFloat() / tSpan * box.width()
            val y = box.bottom - ((value - minV) / span).toFloat() * box.height()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, chartPaint)
        canvas.drawText(number(maxV, 1), p.left, box.top + 8f, mutedPaint)
        canvas.drawText(number(minV, 1), p.left, box.bottom, mutedPaint)
        canvas.drawText(date(points.first().first), box.left, box.bottom + 12f, mutedPaint)
        val end = date(points.last().first)
        canvas.drawText(end, box.right - mutedPaint.measureText(end), box.bottom + 12f, mutedPaint)
        canvas.drawText(unit, box.left + 4f, box.top + 8f, mutedPaint)
        p.y += height + 6f
    }

    private fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().format(dateFormat)

    private fun number(v: Double, digits: Int): String = com.postpole.wheelfile.ui.common.formatNumber(v, digits, locale)

    /** Page and cursor handling: starts a new A4 page whenever the next block doesn't fit. */
    private inner class Pager(private val sink: PageSink) {
        private val width = 595
        private val height = 842
        val left = 40f
        val right = width - 40f
        private val top = 48f
        private val bottom = height - 48f
        private var open = false
        var pageCount = 0
            private set
        lateinit var canvas: Canvas
        var y = top

        init {
            newPage()
        }

        private fun closePage() {
            if (open) {
                canvas.drawText(labels.footer(pageCount), left, height - 24f, mutedPaint)
                sink.finishPage()
            }
            open = false
        }

        private fun newPage() {
            closePage()
            pageCount++
            canvas = sink.startPage(width, height, pageCount)
            open = true
            y = top
        }

        fun ensure(needed: Float) {
            if (y + needed > bottom) newPage()
        }

        fun gap(h: Float) {
            y += h
        }

        fun text(s: String, paint: Paint, gap: Float = 2f) {
            val lineHeight = paint.textSize * 1.3f
            ensure(lineHeight)
            y += paint.textSize
            canvas.drawText(fit(s, paint, right - left), left, y, paint)
            y += lineHeight - paint.textSize + gap
        }

        fun section(s: String, paint: Paint) {
            ensure(paint.textSize * 3)
            y += 6f
            text(s, paint, gap = 3f)
            canvas.drawLine(left, y - 1f, right, y - 1f, linePaint)
            y += 3f
        }

        fun row(label: String, value: String, paint: Paint) {
            ensure(paint.textSize * 1.5f)
            y += paint.textSize
            canvas.drawText(label, left, y, paint)
            canvas.drawText(value, right - paint.measureText(value), y, paint)
            y += paint.textSize * 0.4f + 2f
        }

        /** Four columns: date, odometer, items (takes the rest), cost (right aligned). */
        fun table(header: List<String>, rows: List<List<String>>) {
            val xs = floatArrayOf(left, left + 80f, left + 150f)
            val costRight = right
            val itemsWidth = costRight - 80f - xs[2]
            fun draw(cells: List<String>, paint: Paint) {
                ensure(paint.textSize * 1.6f)
                y += paint.textSize
                canvas.drawText(fit(cells[0], paint, 74f), xs[0], y, paint)
                canvas.drawText(fit(cells[1], paint, 64f), xs[1], y, paint)
                canvas.drawText(fit(cells[2], paint, itemsWidth), xs[2], y, paint)
                canvas.drawText(cells[3], costRight - paint.measureText(cells[3]), y, paint)
                y += paint.textSize * 0.6f + 3f
            }
            draw(header, headerPaint)
            canvas.drawLine(left, y - 2f, right, y - 2f, linePaint)
            rows.forEach { draw(it, bodyPaint) }
        }

        fun finish() = closePage()

        private fun fit(s: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(s) <= maxWidth) return s
            val count = paint.breakText(s, true, maxWidth - paint.measureText("…"), null)
            return s.take(count) + "…"
        }
    }
}
