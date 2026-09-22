package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.print.PrintManager
import android.print.PrintAttributes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import android.util.Base64
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

  var webViewRef: WebView? = null
  private var pendingExportContent: String? = null
  private var filePathCallback: ValueCallback<Array<Uri>>? = null
  private var selectedTreeUri: Uri? = null

  // Google Sign-In Launcher for Drive Sync & Authentication
  private val googleSignInLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
    try {
      val account = task.getResult(ApiException::class.java)
      if (account != null) {
        val email = account.email ?: "Google Account"
        Toast.makeText(this, "Signed in as $email", Toast.LENGTH_SHORT).show()
        val json = JSONObject().apply {
          put("email", account.email ?: "")
          put("displayName", account.displayName ?: "")
          put("photoUrl", account.photoUrl?.toString() ?: "")
        }
        val escaped = JSONObject.quote(json.toString())
        webViewRef?.evaluateJavascript(
          "if (window.onGoogleAccountChanged) window.onGoogleAccountChanged($escaped); if (typeof checkGoogleAccountStatus === 'function') checkGoogleAccountStatus();",
          null
        )
      }
    } catch (e: Exception) {
      Log.e("MainActivity", "Google Sign-In failed", e)
      Toast.makeText(this, "Google Sign-In: ${e.message ?: "Cancelled"}", Toast.LENGTH_SHORT).show()
    }
  }

  fun checkSilentSignIn() {
    try {
      val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
          Scope("https://www.googleapis.com/auth/drive.file"),
          Scope("https://www.googleapis.com/auth/drive.appdata")
        )
        .build()
      val client = GoogleSignIn.getClient(this, gso)
      client.silentSignIn().addOnCompleteListener { task ->
        try {
          val account = task.getResult(ApiException::class.java)
          if (account != null) {
            val json = JSONObject().apply {
              put("email", account.email ?: "")
              put("displayName", account.displayName ?: "")
              put("photoUrl", account.photoUrl?.toString() ?: "")
            }
            val escaped = JSONObject.quote(json.toString())
            runOnUiThread {
              webViewRef?.evaluateJavascript(
                "if (window.onGoogleAccountChanged) window.onGoogleAccountChanged($escaped); if (typeof checkGoogleAccountStatus === 'function') checkGoogleAccountStatus();",
                null
              )
            }
          }
        } catch (e: Exception) {
          Log.d("MainActivity", "Silent sign-in not available: ${e.message}")
        }
      }
    } catch (e: Exception) {
      Log.d("MainActivity", "Silent sign-in error: ${e.message}")
    }
  }

  fun requestGoogleSignIn() {
    try {
      val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
          Scope("https://www.googleapis.com/auth/drive.file"),
          Scope("https://www.googleapis.com/auth/drive.appdata")
        )
        .build()
      val client = GoogleSignIn.getClient(this, gso)
      googleSignInLauncher.launch(client.signInIntent)
    } catch (e: Exception) {
      Toast.makeText(this, "Could not start Google Sign-In: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun requestGoogleSignOut() {
    try {
      val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .build()
      val client = GoogleSignIn.getClient(this, gso)
      client.signOut().addOnCompleteListener {
        Toast.makeText(this, "Logged out from Google Drive", Toast.LENGTH_SHORT).show()
        webViewRef?.evaluateJavascript(
          "if (window.onGoogleAccountChanged) window.onGoogleAccountChanged(null); if (typeof checkGoogleAccountStatus === 'function') checkGoogleAccountStatus();",
          null
        )
      }
    } catch (e: Exception) {
      Toast.makeText(this, "Sign-out error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun getGoogleAccountJson(): String {
    val account = GoogleSignIn.getLastSignedInAccount(this) ?: return ""
    val json = JSONObject().apply {
      put("email", account.email ?: "")
      put("displayName", account.displayName ?: "")
      put("photoUrl", account.photoUrl?.toString() ?: "")
    }
    return json.toString()
  }

  fun isGoogleSignedIn(): Boolean {
    return GoogleSignIn.getLastSignedInAccount(this) != null
  }

  // Folder Picker Launcher for Auto-Export to Local Storage
  private val openDocumentTreeLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, takeFlags)
        selectedTreeUri = uri

        val prefs = getSharedPreferences("khatabook_native_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("auto_export_tree_uri", uri.toString()).apply()

        // Automatically create and verify Khata_App_Backups subfolder
        try {
          val treeDocId = DocumentsContract.getTreeDocumentId(uri)
          val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
          getOrCreateSubFolder(uri, parentDocUri, "Khata_App_Backups")
        } catch (_: Exception) {}

        val displayPath = getDisplayPathFromTreeUri(uri)
        runOnUiThread {
          Toast.makeText(this, "Storage folder set: $displayPath (Subfolder: Khata_App_Backups)", Toast.LENGTH_SHORT).show()
          val escapedPath = JSONObject.quote(displayPath)
          val escapedUri = JSONObject.quote(uri.toString())
          webViewRef?.evaluateJavascript(
            "window.onAndroidFolderSelected && window.onAndroidFolderSelected($escapedPath, $escapedUri)",
            null
          )
        }
      } catch (e: Exception) {
        runOnUiThread {
          Toast.makeText(this, "Failed to grant folder permission: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  // System File Manager Launcher for Export (Save As... to Drive or Storage)
  private val exportLauncher = registerForActivityResult(
    ActivityResultContracts.CreateDocument("*/*")
  ) { uri: Uri? ->
    if (uri != null && pendingExportContent != null) {
      try {
        contentResolver.openOutputStream(uri)?.use { output ->
          output.write(pendingExportContent!!.toByteArray(Charsets.UTF_8))
          output.flush()
        }
        runOnUiThread {
          Toast.makeText(this, "Data exported successfully!", Toast.LENGTH_SHORT).show()
          webViewRef?.evaluateJavascript("window.onAndroidExportSuccess && window.onAndroidExportSuccess()", null)
        }
      } catch (e: Exception) {
        runOnUiThread {
          Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
    pendingExportContent = null
  }

  // System File Manager Launcher for Import (.db or .json file)
  private val importLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val content = contentResolver.openInputStream(uri)?.use { input ->
          input.bufferedReader().readText()
        } ?: ""

        var fileName = "imported.db"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (cursor.moveToFirst() && nameIndex >= 0) {
            fileName = cursor.getString(nameIndex)
          }
        }

        runOnUiThread {
          val escapedContent = JSONObject.quote(content)
          val escapedName = JSONObject.quote(fileName)
          webViewRef?.evaluateJavascript(
            "window.onAndroidFileImported && window.onAndroidFileImported($escapedContent, $escapedName)",
            null
          )
        }
      } catch (e: Exception) {
        runOnUiThread {
          Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  // Contact Picker Launcher for adding customer from phone contact list
  // Uses ACTION_PICK with Phone.CONTENT_URI so that the system grants direct URI read permission to the chosen phone item,
  // reliably retrieving both customer name AND phone number without requiring dangerous runtime permissions.
  private val contactPhonePickerLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val uri = result.data?.data
      if (uri != null) {
        extractAndDispatchContact(uri)
      }
    }
  }

  private val fallbackContactPickerLauncher = registerForActivityResult(
    ActivityResultContracts.PickContact()
  ) { uri: Uri? ->
    if (uri != null) {
      extractAndDispatchContact(uri)
    }
  }

  private fun extractAndDispatchContact(uri: Uri) {
    var contactName = ""
    var contactNumber = ""

    try {
      // 1. Primary: Query the picked URI directly. When picked via Phone.CONTENT_URI, this URI points
      // directly to the Phone data row and has temporary read permission granted by the system.
      val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
      )
      contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
          if (nameIndex >= 0) {
            contactName = cursor.getString(nameIndex) ?: ""
          }
          val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
          if (numIndex >= 0) {
            contactNumber = cursor.getString(numIndex) ?: ""
          }
        }
      }
    } catch (e: Exception) {
      // Ignore and proceed to fallbacks
    }

    // 2. Secondary fallback: If either name or number is still empty, perform fallback queries on uri
    if (contactName.isEmpty() || contactNumber.isEmpty()) {
      try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            if (contactName.isEmpty()) {
              val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
              if (nameIdx >= 0) {
                contactName = cursor.getString(nameIdx) ?: ""
              }
            }

            if (contactNumber.isEmpty()) {
              val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
              if (numIdx >= 0) {
                contactNumber = cursor.getString(numIdx) ?: ""
              }
              if (contactNumber.isEmpty()) {
                val dataIdx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                if (dataIdx >= 0) {
                  contactNumber = cursor.getString(dataIdx) ?: ""
                }
              }
            }

            // If number is still empty and this was a raw Contacts URI with _ID, query child data directory
            if (contactNumber.isEmpty()) {
              val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
              val contactId = if (idIdx >= 0) cursor.getString(idIdx) else ""

              if (contactId.isNotEmpty()) {
                try {
                  val dataUri = Uri.withAppendedPath(uri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
                  contentResolver.query(
                    dataUri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                    null
                  )?.use { dCursor ->
                    if (dCursor.moveToFirst()) {
                      val dNumIdx = dCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                      if (dNumIdx >= 0) {
                        contactNumber = dCursor.getString(dNumIdx) ?: ""
                      }
                    }
                  }
                } catch (e: Exception) {
                  // Ignore
                }

                if (contactNumber.isEmpty()) {
                  try {
                    contentResolver.query(
                      ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                      arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                      "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                      arrayOf(contactId),
                      null
                    )?.use { pCursor ->
                      if (pCursor.moveToFirst()) {
                        val pNumIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (pNumIdx >= 0) {
                          contactNumber = pCursor.getString(pNumIdx) ?: ""
                        }
                      }
                    }
                  } catch (e: Exception) {
                    // Ignore
                  }
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        // Ignore
      }
    }

    // Clean phone number: keep digits and leading '+'
    contactNumber = contactNumber.replace(Regex("[^0-9+]"), "")

    runOnUiThread {
      val escapedName = JSONObject.quote(contactName)
      val escapedPhone = JSONObject.quote(contactNumber)
      webViewRef?.evaluateJavascript(
        "window.onAndroidContactPicked && window.onAndroidContactPicked($escapedName, $escapedPhone)",
        null
      )
    }
  }

  // File Chooser for generic <input type="file">
  private val fileChooserLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val uri = result.data?.data
    if (result.resultCode == Activity.RESULT_OK && uri != null) {
      filePathCallback?.onReceiveValue(arrayOf(uri))
    } else {
      filePathCallback?.onReceiveValue(null)
    }
    filePathCallback = null
  }

  fun launchContactPicker() {
    runOnUiThread {
      try {
        val phoneIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPhonePickerLauncher.launch(phoneIntent)
      } catch (e: Exception) {
        try {
          fallbackContactPickerLauncher.launch(null)
        } catch (e2: Exception) {
          Toast.makeText(this, "Could not open contacts: ${e2.message}", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  fun launchSmsIntent(phoneNumber: String, message: String) {
    try {
      val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "")
      val uri = Uri.parse("smsto:${Uri.encode(cleanPhone)}")
      val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra("sms_body", message)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      startActivity(intent)
    } catch (e: Exception) {
      try {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "")
        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
          data = Uri.parse("sms:${Uri.encode(cleanPhone)}?body=${Uri.encode(message)}")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(fallbackIntent)
      } catch (err: Exception) {
        Toast.makeText(this, "No SMS app found: ${err.message}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun launchWhatsAppIntent(phoneNumber: String, message: String) {
    try {
      var cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
      if (cleanPhone.length == 10) {
        // Prepend India 91 if 10 digits
        cleanPhone = "91$cleanPhone"
      }
      val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
      val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(url)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      startActivity(intent)
    } catch (e: Exception) {
      Toast.makeText(this, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun launchDriveShare(fileName: String, content: String, mimeType: String) {
    pendingExportContent = content
    try {
      // 1. First offer Storage Access Framework / Google Drive direct file creation
      exportLauncher.launch(fileName)
    } catch (e: Exception) {
      // 2. Fallback to generic text/file share intent (which includes Drive)
      try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = if (mimeType.isNotEmpty()) mimeType else "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, fileName)
          putExtra(Intent.EXTRA_TEXT, content)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(Intent.createChooser(shareIntent, "Save or Share to Google Drive"))
      } catch (err: Exception) {
        Toast.makeText(this, "Export failed: ${err.message}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun launchSharePdf(dataJsonStr: String, targetApp: String) {
    try {
      val dataJson = JSONObject(dataJsonStr)
      val pdfFile = PdfStatementGenerator.generateStatementPdf(this, dataJson)
      val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", pdfFile)

      val custName = dataJson.optString("customerName", "Customer")
      val storeName = dataJson.optString("storeName", "Super Hisab")
      val netBalance = dataJson.optString("netBalance", "0")
      val currency = dataJson.optString("currency", "₹")
      val balanceType = dataJson.optString("balanceType", "DUE")
      val balanceLabel = if (balanceType == "DUE") "Pending Balance" else "Account Balance"

      val defaultMsg = "Hello $custName, please find attached your official ledger statement from $storeName.\n$balanceLabel: $currency$netBalance"
      val shareMsg = dataJson.optString("shareMessage", defaultMsg).ifEmpty { defaultMsg }

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "$custName - Account Statement ($storeName)")
        putExtra(Intent.EXTRA_TEXT, shareMsg)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      if (targetApp.equals("whatsapp", ignoreCase = true)) {
        val waIntent = Intent(shareIntent).apply {
          setPackage("com.whatsapp")
        }
        try {
          startActivity(waIntent)
          return
        } catch (_: Exception) {
          try {
            val waBusinessIntent = Intent(shareIntent).apply {
              setPackage("com.whatsapp.w4b")
            }
            startActivity(waBusinessIntent)
            return
          } catch (_: Exception) {}
        }
      }

      startActivity(Intent.createChooser(shareIntent, "Send PDF Statement"))
    } catch (e: Exception) {
      Toast.makeText(this, "Failed to generate or share PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  fun launchViewPdf(dataJsonStr: String) {
    try {
      val dataJson = JSONObject(dataJsonStr)
      val pdfFile = PdfStatementGenerator.generateStatementPdf(this, dataJson)
      val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", pdfFile)

      val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      startActivity(Intent.createChooser(viewIntent, "Open PDF Statement"))
    } catch (e: Exception) {
      Toast.makeText(this, "Could not open PDF viewer: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun launchShareUpiQr(base64Image: String, customerName: String, amount: String, upiUri: String, targetApp: String) {
    try {
      val cleanBase64 = if (base64Image.contains(",")) {
        base64Image.substringAfter(",")
      } else {
        base64Image
      }
      val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

      val sharedFolder = File(cacheDir, "shared_files").apply { mkdirs() }
      val qrFile = File(sharedFolder, "upi_qr_${System.currentTimeMillis()}.png")
      FileOutputStream(qrFile).use { it.write(imageBytes) }

      val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", qrFile)

      val prefs = getSharedPreferences("khatabook_native_prefs", Context.MODE_PRIVATE)
      val storeName = prefs.getString("store_name", "Super Hisab") ?: "Super Hisab"

      val shareMsg = "Payment QR Code for $customerName\nPending Balance: ₹$amount\nStore: $storeName\n\nPay directly with any UPI app (GPay, PhonePe, Paytm, BHIM):\n$upiUri"

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "UPI Payment QR - $customerName")
        putExtra(Intent.EXTRA_TEXT, shareMsg)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      if (targetApp.equals("whatsapp", ignoreCase = true)) {
        val waIntent = Intent(shareIntent).apply {
          setPackage("com.whatsapp")
        }
        try {
          startActivity(waIntent)
          return
        } catch (_: Exception) {
          try {
            val waBusinessIntent = Intent(shareIntent).apply {
              setPackage("com.whatsapp.w4b")
            }
            startActivity(waBusinessIntent)
            return
          } catch (_: Exception) {}
        }
      }

      startActivity(Intent.createChooser(shareIntent, "Share UPI QR Code"))
    } catch (e: Exception) {
      Toast.makeText(this, "Failed to share UPI QR: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  fun launchOpenUpiApp(upiUri: String) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
      val chooser = Intent.createChooser(intent, "Pay via UPI App")
      startActivity(chooser)
    } catch (e: Exception) {
      Toast.makeText(this, "No supported UPI app found to open link", Toast.LENGTH_SHORT).show()
    }
  }

  fun launchShareApk() {
    try {
      val appInfo = applicationInfo
      val apkFile = File(appInfo.sourceDir)
      if (!apkFile.exists()) {
        Toast.makeText(this, "Super Hisab APK file not found", Toast.LENGTH_SHORT).show()
        return
      }

      val exportDir = File(cacheDir, "apk")
      if (!exportDir.exists()) exportDir.mkdirs()
      val exportApk = File(exportDir, "SuperHisab.apk")
      apkFile.copyTo(exportApk, overwrite = true)

      val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", exportApk)

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Super Hisab APK - Direct Installer")
        putExtra(Intent.EXTRA_TEXT, "Here is the Super Hisab offline app APK file. Developed by Sk Ajahar. Install it to manage your retail ledger and store accounts!")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      startActivity(Intent.createChooser(shareIntent, "Share Super Hisab APK via"))
    } catch (err: Exception) {
      Log.e("SuperHisab", "Failed to share APK: ${err.message}", err)
      Toast.makeText(this, "Failed to share APK: ${err.message}", Toast.LENGTH_LONG).show()
    }
  }

  fun launchPrintJob() {
    try {
      val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
      val printAdapter = webViewRef?.createPrintDocumentAdapter("Statement_Report")
      if (printManager != null && printAdapter != null) {
        val jobName = "Khata_Statement_${System.currentTimeMillis()}"
        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
      }
    } catch (e: Exception) {
      Toast.makeText(this, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun startExport(fileName: String, content: String) {
    pendingExportContent = content
    try {
      exportLauncher.launch(fileName)
    } catch (e: Exception) {
      Toast.makeText(this, "Cannot open file manager: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun launchFolderPicker() {
    try {
      openDocumentTreeLauncher.launch(null)
    } catch (e: Exception) {
      Toast.makeText(this, "Could not open folder picker: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun getDisplayPathFromTreeUri(uri: Uri): String {
    return try {
      val docId = DocumentsContract.getTreeDocumentId(uri)
      if (docId.contains(":")) {
        val parts = docId.split(":", limit = 2)
        val storagePrefix = if (parts[0].equals("primary", ignoreCase = true)) "Phone Storage" else parts[0]
        val sub = parts.getOrNull(1)?.trim('/') ?: ""
        if (sub.isNotEmpty()) "$storagePrefix > $sub" else storagePrefix
      } else {
        if (docId.isNotEmpty()) docId else "Phone Storage Folder"
      }
    } catch (_: Exception) {
      "Phone Storage Folder"
    }
  }

  private fun getOrCreateSubFolder(treeUri: Uri, parentDocUri: Uri, folderName: String): Uri {
    return try {
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri,
        DocumentsContract.getDocumentId(parentDocUri)
      )
      var foundUri: Uri? = null
      contentResolver.query(
        childrenUri,
        arrayOf(
          DocumentsContract.Document.COLUMN_DOCUMENT_ID,
          DocumentsContract.Document.COLUMN_DISPLAY_NAME,
          DocumentsContract.Document.COLUMN_MIME_TYPE
        ),
        null,
        null,
        null
      )?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        while (cursor.moveToNext()) {
          val name = cursor.getString(nameIdx)
          val mime = cursor.getString(mimeIdx)
          if (name.equals(folderName, ignoreCase = true) && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            val docId = cursor.getString(idIdx)
            foundUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            break
          }
        }
      }
      foundUri ?: DocumentsContract.createDocument(
        contentResolver,
        parentDocUri,
        DocumentsContract.Document.MIME_TYPE_DIR,
        folderName
      ) ?: parentDocUri
    } catch (_: Exception) {
      parentDocUri
    }
  }

  fun saveAutoExportToLocalStorage(fileName: String, content: String, mimeType: String): Boolean {
    val prefs = getSharedPreferences("khatabook_native_prefs", Context.MODE_PRIVATE)
    val savedUriString = prefs.getString("auto_export_tree_uri", null) ?: selectedTreeUri?.toString()
    if (savedUriString.isNullOrEmpty()) {
      return false
    }

    return try {
      val treeUri = Uri.parse(savedUriString)
      val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
      val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
      val targetDirUri = getOrCreateSubFolder(treeUri, parentDocUri, "Khata_App_Backups")

      val actualMime = when {
        mimeType.isNotEmpty() -> mimeType
        fileName.endsWith(".json", ignoreCase = true) -> "application/json"
        fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
        fileName.endsWith(".db", ignoreCase = true) -> "application/octet-stream"
        else -> "text/plain"
      }

      val createdFileUri = DocumentsContract.createDocument(
        contentResolver,
        targetDirUri,
        actualMime,
        fileName
      ) ?: return false

      contentResolver.openOutputStream(createdFileUri)?.use { output ->
        output.write(content.toByteArray(Charsets.UTF_8))
        output.flush()
      }
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  fun startImport() {
    try {
      importLauncher.launch(
        arrayOf(
          "*/*",
          "application/octet-stream",
          "application/json",
          "text/plain",
          "text/csv",
          "text/comma-separated-values"
        )
      )
    } catch (e: Exception) {
      Toast.makeText(this, "Cannot open file manager: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  fun isEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("google_sdk", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
        Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.HARDWARE.contains("cutf", ignoreCase = true) ||
        Build.HARDWARE.contains("cheeps", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk", ignoreCase = true) ||
        Build.PRODUCT.contains("vbox", ignoreCase = true) ||
        Build.PRODUCT.contains("emulator", ignoreCase = true) ||
        Build.BRAND.startsWith("generic") ||
        Build.DEVICE.startsWith("generic")
  }

  fun applyPreferredRefreshRate(fps: Float) {
    if (fps <= 0f) return
    try {
      if (isEmulator()) return

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          this.display
        } else {
          @Suppress("DEPRECATION")
          windowManager.defaultDisplay
        }
        val modes = display?.supportedModes ?: emptyArray()
        if (modes.size > 1 && modes.any { Math.abs(it.refreshRate - fps) < 5f }) {
          val params = window.attributes
          params.preferredRefreshRate = fps
          window.attributes = params
        }
      }
    } catch (e: Throwable) {
      // Safe no-op on platforms without variable refresh rate support
    }
  }

  fun getSupportedRefreshRatesJson(): String {
    return JSONArray(listOf(60, 90, 120)).toString()
  }

  fun triggerVibrate(durationMs: Long) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.vibrate(
          VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        )
      } else {
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION")
          vibrator?.vibrate(durationMs)
        }
      }
    } catch (_: Exception) {}
  }

  fun handleShowFileChooser(
    filePathCallback: ValueCallback<Array<Uri>>?,
    fileChooserParams: WebChromeClient.FileChooserParams?
  ): Boolean {
    this.filePathCallback?.onReceiveValue(null)
    this.filePathCallback = filePathCallback
    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
      type = "*/*"
      addCategory(Intent.CATEGORY_OPENABLE)
    }
    return try {
      fileChooserLauncher.launch(intent)
      true
    } catch (e: Exception) {
      this.filePathCallback = null
      false
    }
  }

  fun applyKeepScreenOn(enabled: Boolean) {
    try {
      if (enabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
      val prefs = getSharedPreferences("khatabook_native_prefs", Context.MODE_PRIVATE)
      prefs.edit().putBoolean("keep_screen_on", enabled).apply()
    } catch (_: Exception) {}
  }

  fun isKeepScreenOnEnabled(): Boolean {
    val prefs = getSharedPreferences("khatabook_native_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("keep_screen_on", false)
  }

  var pendingTargetTab: String? = null

  fun requestPinCalculatorWidget(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val appWidgetManager = getSystemService(AppWidgetManager::class.java)
      val myProvider = ComponentName(this, CalculatorWidgetProvider::class.java)
      if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
        try {
          val success = appWidgetManager.requestPinAppWidget(myProvider, null, null)
          if (success) {
            Toast.makeText(this, "Select 'Add to Home screen' to place widget", Toast.LENGTH_SHORT).show()
            return true
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }

      val shortcutManager = getSystemService(ShortcutManager::class.java)
      if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
        try {
          val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.ACTION_OPEN_CALCULATOR"
            putExtra("OPEN_TAB", "calculator")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          }
          val pinShortcutInfo = ShortcutInfo.Builder(this, "shortcut_calc_main")
            .setShortLabel("Calculator")
            .setLongLabel("Main Khata Calculator")
            .setIcon(Icon.createWithResource(this, R.drawable.ic_widget_calc))
            .setIntent(intent)
            .build()
          shortcutManager.requestPinShortcut(pinShortcutInfo, null)
          Toast.makeText(this, "Select 'Add to Home screen' for 1-tap Calculator", Toast.LENGTH_SHORT).show()
          return true
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }
    Toast.makeText(this, "Tip: Long-press your home screen → Widgets → select Khata Calculator", Toast.LENGTH_LONG).show()
    return false
  }

  private fun handleCalculatorIntent(incomingIntent: Intent?) {
    if (incomingIntent == null) return
    val openTab = incomingIntent.getStringExtra("OPEN_TAB")
    val action = incomingIntent.action
    if (openTab == "calculator" || action == "com.example.ACTION_OPEN_CALCULATOR") {
      if (webViewRef != null) {
        runOnUiThread {
          webViewRef?.evaluateJavascript(
            "window.openMainCalculatorDirectly ? window.openMainCalculatorDirectly() : (window.switchTab && window.switchTab(1))",
            null
          )
        }
      } else {
        pendingTargetTab = "calculator"
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleCalculatorIntent(intent)
  }

  private var lastBackPressTime = 0L
  private var isHandlingBack = false

  fun handleGlobalBack() {
    val now = System.currentTimeMillis()
    if (isHandlingBack) return

    val webView = webViewRef
    if (webView == null) {
      handleExitBackPress()
      return
    }

    isHandlingBack = true
    webView.evaluateJavascript(
      """
      (function() {
        try {
          if (typeof window.handleAndroidBack === 'function') {
            return Boolean(window.handleAndroidBack());
          }
        } catch(e) {
          console.error("Error in handleAndroidBack:", e);
        }
        return false;
      })();
      """.trimIndent()
    ) { result ->
      isHandlingBack = false
      val handled = result == "true" || result == "\"true\""
      if (!handled) {
        handleExitBackPress()
      }
    }
  }

  fun handleExitBackPress() {
    val now = System.currentTimeMillis()
    if (now - lastBackPressTime < 2000) {
      finish()
    } else {
      lastBackPressTime = now
      Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
      handleGlobalBack()
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js").mkdirs()
      File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm").mkdirs()
      File(cacheDir, "WebView/Default/HTTP Cache").mkdirs()
    } catch (_: Exception) {}
    handleCalculatorIntent(intent)
    enableEdgeToEdge()
    if (isKeepScreenOnEnabled()) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        handleGlobalBack()
      }
    })

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = androidx.compose.ui.graphics.Color(0xFF09090B)
        ) {
          KhataBookWebView(activity = this)
        }
      }
    }
  }
}

class WebAppInterface(private val activity: MainActivity) {
  @JavascriptInterface
  fun setKeepScreenOn(enabled: Boolean) {
    activity.runOnUiThread {
      activity.applyKeepScreenOn(enabled)
    }
  }

  @JavascriptInterface
  fun isKeepScreenOn(): Boolean {
    return activity.isKeepScreenOnEnabled()
  }
  @JavascriptInterface
  fun vibrate(durationMs: Long) {
    activity.triggerVibrate(durationMs)
  }

  @JavascriptInterface
  fun exportDatabaseFile(fileName: String, content: String) {
    activity.runOnUiThread {
      activity.startExport(fileName, content)
    }
  }

  @JavascriptInterface
  fun openFileManagerToImport() {
    activity.runOnUiThread {
      activity.startImport()
    }
  }

  @JavascriptInterface
  fun pickContact() {
    activity.runOnUiThread {
      activity.launchContactPicker()
    }
  }

  @JavascriptInterface
  fun sendDirectSms(phoneNumber: String, message: String) {
    activity.runOnUiThread {
      activity.launchSmsIntent(phoneNumber, message)
    }
  }

  @JavascriptInterface
  fun sendWhatsApp(phoneNumber: String, message: String) {
    activity.runOnUiThread {
      activity.launchWhatsAppIntent(phoneNumber, message)
    }
  }

  @JavascriptInterface
  fun shareToGoogleDrive(fileName: String, content: String, mimeType: String) {
    activity.runOnUiThread {
      activity.launchDriveShare(fileName, content, mimeType)
    }
  }

  @JavascriptInterface
  fun sendPdfStatement(dataJsonStr: String, targetApp: String) {
    activity.runOnUiThread {
      activity.launchSharePdf(dataJsonStr, targetApp)
    }
  }

  @JavascriptInterface
  fun viewPdfStatement(dataJsonStr: String) {
    activity.runOnUiThread {
      activity.launchViewPdf(dataJsonStr)
    }
  }

  @JavascriptInterface
  fun shareUpiQr(base64Image: String, customerName: String, amount: String, upiUri: String, targetApp: String) {
    activity.runOnUiThread {
      activity.launchShareUpiQr(base64Image, customerName, amount, upiUri, targetApp)
    }
  }

  @JavascriptInterface
  fun openUpiApp(upiUri: String) {
    activity.runOnUiThread {
      activity.launchOpenUpiApp(upiUri)
    }
  }

  @JavascriptInterface
  fun printDocument() {
    activity.runOnUiThread {
      activity.launchPrintJob()
    }
  }

  @JavascriptInterface
  fun selectStorageFolder() {
    activity.runOnUiThread {
      activity.launchFolderPicker()
    }
  }

  @JavascriptInterface
  fun getSavedStorageFolder(): String {
    val prefs = activity.getSharedPreferences("khatabook_native_prefs", Context.MODE_PRIVATE)
    val uriStr = prefs.getString("auto_export_tree_uri", null) ?: return ""
    return try {
      val uri = Uri.parse(uriStr)
      val folderPath = activity.getDisplayPathFromTreeUri(uri)
      val obj = JSONObject()
      obj.put("path", folderPath)
      obj.put("uri", uriStr)
      obj.toString()
    } catch (_: Exception) {
      ""
    }
  }

  @JavascriptInterface
  fun autoExportToLocalStorage(fileName: String, content: String, mimeType: String): Boolean {
    val success = activity.saveAutoExportToLocalStorage(fileName, content, mimeType)
    if (success) {
      activity.runOnUiThread {
        Toast.makeText(activity, "Auto-backup saved to /Khata_App_Backups", Toast.LENGTH_SHORT).show()
      }
    }
    return success
  }

  @JavascriptInterface
  fun setPreferredRefreshRate(fps: Float) {
    activity.runOnUiThread {
      activity.applyPreferredRefreshRate(fps)
    }
  }

  @JavascriptInterface
  fun getSupportedRefreshRates(): String {
    return activity.getSupportedRefreshRatesJson()
  }

  @JavascriptInterface
  fun pinCalculatorWidget() {
    activity.runOnUiThread {
      activity.requestPinCalculatorWidget()
    }
  }

  @JavascriptInterface
  fun shareApk() {
    activity.runOnUiThread {
      activity.launchShareApk()
    }
  }

  @JavascriptInterface
  fun googleSignIn() {
    activity.runOnUiThread {
      activity.requestGoogleSignIn()
    }
  }

  @JavascriptInterface
  fun silentGoogleSignIn() {
    activity.runOnUiThread {
      activity.checkSilentSignIn()
    }
  }

  @JavascriptInterface
  fun googleSignOut() {
    activity.runOnUiThread {
      activity.requestGoogleSignOut()
    }
  }

  @JavascriptInterface
  fun getGoogleAccount(): String {
    return activity.getGoogleAccountJson()
  }

  @JavascriptInterface
  fun isGoogleSignedIn(): Boolean {
    return activity.isGoogleSignedIn()
  }

  @JavascriptInterface
  fun isPinWidgetSupported(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val appWidgetManager = activity.getSystemService(AppWidgetManager::class.java)
      if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) return true
      val shortcutManager = activity.getSystemService(ShortcutManager::class.java)
      if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) return true
    }
    return false
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KhataBookWebView(activity: MainActivity, modifier: Modifier = Modifier) {
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }

  BackHandler(enabled = true) {
    activity.handleGlobalBack()
  }

  AndroidView(
    modifier = modifier
      .fillMaxSize()
      .testTag("khata_book_webview"),
    factory = { ctx ->
      WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#09090B"))
        // Use LAYER_TYPE_NONE so WebView renders directly with the window compositor without forcing
        // an offscreen EGL hardware layer that attempts to open /dev/dri/renderD* in Mesa environments.
        setLayerType(android.view.View.LAYER_TYPE_NONE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        setOnKeyListener { _, keyCode, event ->
          if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            activity.handleGlobalBack()
            true
          } else {
            false
          }
        }

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          useWideViewPort = true
          loadWithOverviewMode = true
          setSupportZoom(false)
          displayZoomControls = false
          mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
          cacheMode = WebSettings.LOAD_DEFAULT
        }

        addJavascriptInterface(WebAppInterface(activity), "AndroidNative")

        webChromeClient = object : WebChromeClient() {
          override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
          ): Boolean {
            return activity.handleShowFileChooser(filePathCallback, fileChooserParams)
          }

          override fun onJsConfirm(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
          ): Boolean {
            AlertDialog.Builder(activity)
              .setTitle("Super Hisab")
              .setMessage(message ?: "")
              .setPositiveButton("Confirm") { _, _ -> result?.confirm() }
              .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
              .setOnCancelListener { result?.cancel() }
              .show()
            return true
          }

          override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
          ): Boolean {
            AlertDialog.Builder(activity)
              .setTitle("Super Hisab")
              .setMessage(message ?: "")
              .setPositiveButton("OK") { _, _ -> result?.confirm() }
              .setOnCancelListener { result?.cancel() }
              .show()
            return true
          }

          override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
            android.util.Log.d(
              "KhataBookJS",
              "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}"
            )
            return true
          }
        }

        webViewClient = object : WebViewClient() {
          override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
          ) {
            android.util.Log.w("KhataBook", "WebView warning: $description ($errorCode) for $failingUrl")
          }

          override fun onRenderProcessGone(
            view: WebView?,
            detail: android.webkit.RenderProcessGoneDetail?
          ): Boolean {
            val crashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              detail?.didCrash() == true
            } else {
              false
            }
            android.util.Log.w("KhataBook", "Render process gone: didCrash=$crashed")
            try {
              view?.destroy()
            } catch (_: Exception) {}
            return true
          }

          override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.evaluateJavascript(
              """
              (function() {
                if (window.AndroidNative && window.AndroidNative.vibrate) {
                  var origVibrate = navigator.vibrate;
                  navigator.vibrate = function(pattern) {
                    var ms = Array.isArray(pattern) ? (pattern[0] || 15) : (pattern || 15);
                    window.AndroidNative.vibrate(ms);
                    if (origVibrate) { try { origVibrate.call(navigator, pattern); } catch(e){} }
                    return true;
                  };
                }
              })();
              """.trimIndent(),
              null
            )

            if (activity.pendingTargetTab == "calculator") {
              activity.pendingTargetTab = null
              view?.postDelayed({
                view.evaluateJavascript(
                  "window.openMainCalculatorDirectly ? window.openMainCalculatorDirectly() : (window.switchTab && window.switchTab(1))",
                  null
                )
              }, 300)
            }

            // Silent Google Sign-In check on load
            activity.checkSilentSignIn()
          }
        }

        loadUrl("file:///android_asset/index.html")
        activity.webViewRef = this
        webViewInstance = this
      }
    },
    update = { webView ->
      activity.webViewRef = webView
      webViewInstance = webView
    }
  )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

