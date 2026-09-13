package com.saimega.vinayakacablenetwork

import android.content.Context
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BluetoothPrinterHelper {

    suspend fun printReceipt(
        context: Context,
        customerName: String,
        customerId: String,
        date: String,
        baseAmount: Double,
        extraCharges: Double,
        totalPaid: Double,
        paymentMode: String,
        paymentNumber: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Find first paired bluetooth printer
                val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                if (printerConnection == null) {
                    return@withContext false
                }

                // Initialize printer. 
                // Settings for 58mm printer: 203 dpi, 48mm printing width, 32 characters per line.
                // Works gracefully on 80mm printers as well (will just occupy the left 48mm).
                val printer = EscPosPrinter(printerConnection, 203, 48f, 32)

                val modeText = if (paymentNumber.isNotEmpty()) "$paymentMode ($paymentNumber)" else paymentMode

                val businessName   = context.getString(R.string.business_name_caps)
                val receiptTitle   = context.getString(R.string.payment_receipt)
                val seriesLabel    = context.getString(R.string.series_number_label)
                val thankYouLine   = context.getString(R.string.thank_you_payment)

                val receiptText = """
                    [C]<b>$businessName</b>
                    [C]$receiptTitle
                    [L]
                    [C]--------------------------------
                    [L]Name: [R]$customerName
                    [L]$seriesLabel: [R]$customerId
                    [L]Date: [R]$date
                    [L]Base Amount: [R]Rs.$baseAmount
                    [L]Extra Charges: [R]Rs.$extraCharges
                    [C]--------------------------------
                    [L]<b>Amount Paid:</b> [R]<b>Rs.$totalPaid</b>
                    [C]--------------------------------
                    [L]Mode: [R]$modeText
                    [L]
                    [C]$thankYouLine
                    [L]
                    [L]
                """.trimIndent()

                // Depending on the printer, printFormattedTextAndCut may cut the paper
                // For 58mm printers without a cutter, it will just feed paper.
                printer.printFormattedTextAndCut(receiptText)
                printer.disconnectPrinter()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
