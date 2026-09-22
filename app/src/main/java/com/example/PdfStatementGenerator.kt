package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfStatementGenerator {

  private const val PAGE_WIDTH = 595
  private const val PAGE_HEIGHT = 842
  private const val MARGIN_LEFT = 36f
  private const val MARGIN_RIGHT = 559f // 595 - 36
  private const val CONTENT_WIDTH = 523f

  // Colors
  private const val COLOR_PRIMARY = 0xFF0F172A.toInt() // Slate 900
  private const val COLOR_ACCENT = 0xFF0D9488.toInt() // Teal 600
  private const val COLOR_MUTED = 0xFF64748B.toInt() // Slate 500
  private const val COLOR_BORDER = 0xFFE2E8F0.toInt() // Slate 200
  private const val COLOR_BG_LIGHT = 0xFFF8FAFC.toInt() // Slate 50
  private const val COLOR_BG_HEADER = 0xFFF1F5F9.toInt() // Slate 100
  private const val COLOR_RED = 0xFFDC2626.toInt() // Red 600
  private const val COLOR_GREEN = 0xFF16A34A.toInt() // Green 600

  fun generateStatementPdf(context: Context, dataJson: JSONObject): File {
    val document = PdfDocument()

    val storeName = dataJson.optString("storeName", "Super Hisab").ifEmpty { "Super Hisab" }
    val customerName = dataJson.optString("customerName", "Customer").ifEmpty { "Customer" }
    val customerPhone = dataJson.optString("customerPhone", "")
    val statementDate = dataJson.optString("date", SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()))
    val currency = dataJson.optString("currency", "₹")
    val totalGave = dataJson.optString("totalGave", "0")
    val totalGot = dataJson.optString("totalGot", "0")
    val netBalance = dataJson.optString("netBalance", "0")
    val balanceType = dataJson.optString("balanceType", "DUE")
    val transactions: JSONArray = dataJson.optJSONArray("transactions") ?: JSONArray()

    val totalTx = transactions.length()

    // Paints
    val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
    }
    val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1f
      color = COLOR_BORDER
    }

    // Determine pagination
    val firstPageCapacity = 16
    val nextPageCapacity = 24

    val totalPages = if (totalTx <= firstPageCapacity) {
      1
    } else {
      1 + ((totalTx - firstPageCapacity + nextPageCapacity - 1) / nextPageCapacity)
    }

    var txIndex = 0

    for (pageNum in 1..totalPages) {
      val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
      val page = document.startPage(pageInfo)
      val canvas = page.canvas

      var currentY = 36f

      if (pageNum == 1) {
        // --- PAGE 1: FULL HEADER & CUSTOMER SUMMARY ---

        // Top Accent Bar
        paintFill.color = COLOR_ACCENT
        canvas.drawRoundRect(RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + 4f), 2f, 2f, paintFill)
        currentY += 16f

        // Brand & Header
        paintBold.color = COLOR_PRIMARY
        paintBold.textSize = 18f
        canvas.drawText(storeName, MARGIN_LEFT, currentY, paintBold)

        paintBold.textSize = 12f
        paintBold.color = COLOR_ACCENT
        val titleText = "ACCOUNT STATEMENT"
        val titleWidth = paintBold.measureText(titleText)
        canvas.drawText(titleText, MARGIN_RIGHT - titleWidth, currentY, paintBold)
        currentY += 14f

        // Subtitle & Statement Details
        paintText.color = COLOR_MUTED
        paintText.textSize = 8.5f
        canvas.drawText("Official Customer Ledger & Transaction History", MARGIN_LEFT, currentY, paintText)

        val metaText = "Date: $statementDate  •  Currency: $currency"
        val metaWidth = paintText.measureText(metaText)
        canvas.drawText(metaText, MARGIN_RIGHT - metaWidth, currentY, paintText)
        currentY += 14f

        // Divider
        canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, paintStroke)
        currentY += 12f

        // Info Cards (Customer on Left, Summary on Right)
        val cardWidth = (CONTENT_WIDTH - 12f) / 2f
        val cardHeight = 62f

        // 1. Customer Card (Left)
        val custCardRect = RectF(MARGIN_LEFT, currentY, MARGIN_LEFT + cardWidth, currentY + cardHeight)
        paintFill.color = COLOR_BG_LIGHT
        canvas.drawRoundRect(custCardRect, 6f, 6f, paintFill)
        canvas.drawRoundRect(custCardRect, 6f, 6f, paintStroke)

        paintBold.color = COLOR_MUTED
        paintBold.textSize = 7.5f
        canvas.drawText("CUSTOMER DETAILS", MARGIN_LEFT + 10f, currentY + 14f, paintBold)

        paintBold.color = COLOR_PRIMARY
        paintBold.textSize = 11.5f
        val displayCustName = if (customerName.length > 28) customerName.take(26) + "..." else customerName
        canvas.drawText(displayCustName, MARGIN_LEFT + 10f, currentY + 30f, paintBold)

        paintText.color = COLOR_MUTED
        paintText.textSize = 8.5f
        val phoneDisplay = if (customerPhone.isNotEmpty()) "Phone: $customerPhone" else "Account: Verified Customer"
        canvas.drawText(phoneDisplay, MARGIN_LEFT + 10f, currentY + 44f, paintText)
        canvas.drawText("Total Transactions: $totalTx", MARGIN_LEFT + 10f, currentY + 54f, paintText)

        // 2. Summary Card (Right)
        val sumCardLeft = MARGIN_LEFT + cardWidth + 12f
        val sumCardRect = RectF(sumCardLeft, currentY, MARGIN_RIGHT, currentY + cardHeight)
        canvas.drawRoundRect(sumCardRect, 6f, 6f, paintFill)
        canvas.drawRoundRect(sumCardRect, 6f, 6f, paintStroke)

        paintBold.color = COLOR_MUTED
        paintBold.textSize = 7.5f
        canvas.drawText("ACCOUNT SUMMARY", sumCardLeft + 10f, currentY + 14f, paintBold)

        paintText.textSize = 8f
        paintText.color = COLOR_PRIMARY
        canvas.drawText("Total Gave (+): $currency$totalGave", sumCardLeft + 10f, currentY + 28f, paintText)
        canvas.drawText("Total Got (-): $currency$totalGot", sumCardLeft + 10f, currentY + 40f, paintText)

        // Net Balance Highlight
        val netStatus = when (balanceType) {
          "DUE" -> "NET DUE (PAYABLE)"
          "RECEIVED" -> "ADVANCE BALANCE"
          else -> "SETTLED (NIL)"
        }
        val netColor = when (balanceType) {
          "DUE" -> COLOR_RED
          "RECEIVED" -> COLOR_GREEN
          else -> COLOR_MUTED
        }
        paintBold.textSize = 9.5f
        paintBold.color = netColor
        canvas.drawText("$netStatus: $currency$netBalance", sumCardLeft + 10f, currentY + 54f, paintBold)

        currentY += cardHeight + 14f

      } else {
        // --- CONTINUATION PAGE HEADER ---
        paintBold.color = COLOR_PRIMARY
        paintBold.textSize = 10f
        canvas.drawText("$storeName  •  Statement for $customerName (Continued)", MARGIN_LEFT, currentY + 10f, paintBold)

        paintText.color = COLOR_MUTED
        paintText.textSize = 8.5f
        val pageSub = "Date: $statementDate  •  Page $pageNum of $totalPages"
        val subW = paintText.measureText(pageSub)
        canvas.drawText(pageSub, MARGIN_RIGHT - subW, currentY + 10f, paintText)

        currentY += 18f
        canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, paintStroke)
        currentY += 10f
      }

      // --- TABLE COLUMN HEADER ---
      val colWDate = 90f
      val colWDesc = 185f
      val colWGave = 80f
      val colWGot = 80f
      val colWBal = 88f

      val colXDate = MARGIN_LEFT
      val colXDesc = colXDate + colWDate
      val colXGave = colXDesc + colWDesc
      val colXGot = colXGave + colWGave
      val colXBal = colXGot + colWGot

      val headerHeight = 22f
      val headerRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + headerHeight)
      paintFill.color = COLOR_BG_HEADER
      canvas.drawRoundRect(headerRect, 4f, 4f, paintFill)
      canvas.drawRoundRect(headerRect, 4f, 4f, paintStroke)

      paintBold.color = COLOR_PRIMARY
      paintBold.textSize = 7.5f

      canvas.drawText("DATE & TIME", colXDate + 8f, currentY + 14f, paintBold)
      canvas.drawText("PARTICULARS / NOTE", colXDesc + 6f, currentY + 14f, paintBold)

      val gaveHead = "GAVE (DR)"
      canvas.drawText(gaveHead, colXGave + colWGave - paintBold.measureText(gaveHead) - 8f, currentY + 14f, paintBold)

      val gotHead = "GOT (CR)"
      canvas.drawText(gotHead, colXGot + colWGot - paintBold.measureText(gotHead) - 8f, currentY + 14f, paintBold)

      val balHead = "BALANCE"
      canvas.drawText(balHead, colXBal + colWBal - paintBold.measureText(balHead) - 8f, currentY + 14f, paintBold)

      currentY += headerHeight

      // --- TABLE DATA ROWS ---
      val rowHeight = 22f
      val pageCapacity = if (pageNum == 1) firstPageCapacity else nextPageCapacity
      var rowsPrintedThisPage = 0

      while (txIndex < totalTx && rowsPrintedThisPage < pageCapacity) {
        val tx = transactions.optJSONObject(txIndex)
        if (tx != null) {
          val isAlt = (rowsPrintedThisPage % 2 == 1)
          if (isAlt) {
            paintFill.color = COLOR_BG_LIGHT
            canvas.drawRect(RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + rowHeight), paintFill)
          }

          // Bottom border line for row
          canvas.drawLine(MARGIN_LEFT, currentY + rowHeight, MARGIN_RIGHT, currentY + rowHeight, paintStroke)

          val txDate = tx.optString("date", "")
          val txNote = tx.optString("note", "Payment / Entry").ifEmpty { "Payment / Entry" }
          val txType = tx.optString("type", "DUE")
          val txAmount = tx.optString("amount", "0")
          val txBalance = tx.optString("balance", "")

          paintText.textSize = 7.5f
          paintText.color = COLOR_MUTED
          canvas.drawText(txDate, colXDate + 8f, currentY + 14f, paintText)

          paintText.color = COLOR_PRIMARY
          val safeNote = if (txNote.length > 32) txNote.take(30) + "..." else txNote
          canvas.drawText(safeNote, colXDesc + 6f, currentY + 14f, paintText)

          if (txType == "DUE") {
            // Gave
            paintBold.textSize = 8f
            paintBold.color = COLOR_RED
            val valStr = "$currency$txAmount"
            canvas.drawText(valStr, colXGave + colWGave - paintBold.measureText(valStr) - 8f, currentY + 14f, paintBold)

            paintText.color = COLOR_MUTED
            canvas.drawText("-", colXGot + (colWGot / 2f) - 2f, currentY + 14f, paintText)
          } else {
            // Got
            paintText.color = COLOR_MUTED
            canvas.drawText("-", colXGave + (colWGave / 2f) - 2f, currentY + 14f, paintText)

            paintBold.textSize = 8f
            paintBold.color = COLOR_GREEN
            val valStr = "$currency$txAmount"
            canvas.drawText(valStr, colXGot + colWGot - paintBold.measureText(valStr) - 8f, currentY + 14f, paintBold)
          }

          if (txBalance.isNotEmpty()) {
            paintBold.textSize = 7.5f
            paintBold.color = COLOR_PRIMARY
            val balStr = "$currency$txBalance"
            canvas.drawText(balStr, colXBal + colWBal - paintBold.measureText(balStr) - 8f, currentY + 14f, paintBold)
          }

          currentY += rowHeight
          rowsPrintedThisPage++
        }
        txIndex++
      }

      // If no transactions at all
      if (totalTx == 0) {
        paintText.color = COLOR_MUTED
        paintText.textSize = 9f
        canvas.drawText("No recorded transactions found for this customer.", MARGIN_LEFT + 16f, currentY + 24f, paintText)
        currentY += 40f
      }

      // --- FINAL TOTALS & FOOTER (On Last Page) ---
      if (pageNum == totalPages) {
        currentY += 8f

        // Totals bar
        val totalRect = RectF(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY + 24f)
        paintFill.color = COLOR_BG_HEADER
        canvas.drawRoundRect(totalRect, 4f, 4f, paintFill)
        canvas.drawRoundRect(totalRect, 4f, 4f, paintStroke)

        paintBold.textSize = 8f
        paintBold.color = COLOR_PRIMARY
        canvas.drawText("TOTALS", colXDesc + 6f, currentY + 15f, paintBold)

        paintBold.color = COLOR_RED
        val totGaveStr = "$currency$totalGave"
        canvas.drawText(totGaveStr, colXGave + colWGave - paintBold.measureText(totGaveStr) - 8f, currentY + 15f, paintBold)

        paintBold.color = COLOR_GREEN
        val totGotStr = "$currency$totalGot"
        canvas.drawText(totGotStr, colXGot + colWGot - paintBold.measureText(totGotStr) - 8f, currentY + 15f, paintBold)

        paintBold.color = COLOR_PRIMARY
        val finalBalStr = "$currency$netBalance"
        canvas.drawText(finalBalStr, colXBal + colWBal - paintBold.measureText(finalBalStr) - 8f, currentY + 15f, paintBold)

        currentY += 36f

        // Authentication & Signatory note
        paintText.color = COLOR_MUTED
        paintText.textSize = 7.5f
        canvas.drawText("This is a verified computer-generated statement issued by $storeName.", MARGIN_LEFT, currentY, paintText)
        canvas.drawText("Thank you for your valued business. For payment or disputes, please contact the merchant.", MARGIN_LEFT, currentY + 11f, paintText)

        // Signature box on right
        paintStroke.color = COLOR_BORDER
        val sigX = MARGIN_RIGHT - 140f
        canvas.drawLine(sigX, currentY + 10f, MARGIN_RIGHT, currentY + 10f, paintStroke)
        paintText.textSize = 7.5f
        canvas.drawText("Authorized Signatory", sigX + 18f, currentY + 22f, paintText)
      }

      // Bottom Page Footer
      val footerY = PAGE_HEIGHT - 28f
      canvas.drawLine(MARGIN_LEFT, footerY - 8f, MARGIN_RIGHT, footerY - 8f, paintStroke)

      paintText.color = COLOR_MUTED
      paintText.textSize = 7.5f
      canvas.drawText("Super Hisab  •  Secure Digital Bahi Khata", MARGIN_LEFT, footerY + 2f, paintText)

      val pageNumberText = "Page $pageNum of $totalPages"
      val pnw = paintText.measureText(pageNumberText)
      canvas.drawText(pageNumberText, MARGIN_RIGHT - pnw, footerY + 2f, paintText)

      document.finishPage(page)
    }

    // Save to cache directory
    val pdfDir = File(context.cacheDir, "pdf")
    if (!pdfDir.exists()) {
      pdfDir.mkdirs()
    }

    val cleanCustName = customerName.replace(Regex("[^a-zA-Z0-9_]"), "_").take(20)
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val pdfFile = File(pdfDir, "Statement_${cleanCustName}_$timeStamp.pdf")

    FileOutputStream(pdfFile).use { output ->
      document.writeTo(output)
    }
    document.close()

    return pdfFile
  }
}
