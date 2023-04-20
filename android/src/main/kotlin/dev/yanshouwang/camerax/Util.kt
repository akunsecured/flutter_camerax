package dev.yanshouwang.camerax

import android.annotation.SuppressLint
import android.graphics.*
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import androidx.camera.core.internal.utils.ImageUtil
import com.google.mlkit.vision.barcode.common.Barcode
import java.io.ByteArrayOutputStream

val Camera.torchable: Boolean
    get() = cameraInfo.hasFlashUnit()

val ImageProxy.yuv: ByteArray
    get() {
        val ySize = y.buffer.remaining()
        val uSize = u.buffer.remaining()
        val vSize = v.buffer.remaining()

        val size = ySize + uSize + vSize
        val data = ByteArray(size)

        var offset = 0
        y.buffer.get(data, offset, ySize)
        offset += ySize
        u.buffer.get(data, offset, uSize)
        offset += uSize
        v.buffer.get(data, offset, vSize)

        return data
    }

val ImageProxy.jpeg: ByteArray
    @SuppressLint("RestrictedApi")
    get() {
        val data = ImageUtil.yuvImageToJpegByteArray(this, null, 100)
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

        // Rotation is required
        val rotationMatrix = Matrix()
        rotationMatrix.postRotate(90F)
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            rotationMatrix,
            true
        )
        bitmap.recycle()

        val outputStream = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return outputStream.toByteArray()
    }

val ImageProxy.PlaneProxy.size
    get() = buffer.remaining()

val ImageProxy.y: ImageProxy.PlaneProxy
    get() = planes[0]

val ImageProxy.u: ImageProxy.PlaneProxy
    get() = planes[1]

val ImageProxy.v: ImageProxy.PlaneProxy
    get() = planes[2]

val Barcode.data: Map<String, Any?>
    get() = mapOf(
        "corners" to cornerPoints?.map { corner -> corner.data }, "format" to format,
        "rawBytes" to rawBytes, "rawValue" to rawValue, "type" to valueType,
        "calendarEvent" to calendarEvent?.data, "contactInfo" to contactInfo?.data,
        "driverLicense" to driverLicense?.data, "email" to email?.data,
        "geoPoint" to geoPoint?.data, "phone" to phone?.data, "sms" to sms?.data,
        "url" to url?.data, "wifi" to wifi?.data
    )

val Point.data: Map<String, Double>
    get() = mapOf("x" to x.toDouble(), "y" to y.toDouble())

val Barcode.CalendarEvent.data: Map<String, Any?>
    get() = mapOf(
        "description" to description, "end" to end?.rawValue, "location" to location,
        "organizer" to organizer, "start" to start?.rawValue, "status" to status,
        "summary" to summary
    )

val Barcode.ContactInfo.data: Map<String, Any?>
    get() = mapOf(
        "addresses" to addresses.map { address -> address.data },
        "emails" to emails.map { email -> email.data }, "name" to name?.data,
        "organization" to organization, "phones" to phones.map { phone -> phone.data },
        "title" to title, "urls" to urls
    )

val Barcode.Address.data: Map<String, Any?>
    get() = mapOf("addressLines" to addressLines, "type" to type)

val Barcode.PersonName.data: Map<String, Any?>
    get() = mapOf(
        "first" to first, "formattedName" to formattedName, "last" to last,
        "middle" to middle, "prefix" to prefix, "pronunciation" to pronunciation,
        "suffix" to suffix
    )

val Barcode.DriverLicense.data: Map<String, Any?>
    get() = mapOf(
        "addressCity" to addressCity, "addressState" to addressState,
        "addressStreet" to addressStreet, "addressZip" to addressZip, "birthDate" to birthDate,
        "documentType" to documentType, "expiryDate" to expiryDate, "firstName" to firstName,
        "gender" to gender, "issueDate" to issueDate, "issuingCountry" to issuingCountry,
        "lastName" to lastName, "licenseNumber" to licenseNumber, "middleName" to middleName
    )

val Barcode.Email.data: Map<String, Any?>
    get() = mapOf("address" to address, "body" to body, "subject" to subject, "type" to type)

val Barcode.GeoPoint.data: Map<String, Any?>
    get() = mapOf("latitude" to lat, "longitude" to lng)

val Barcode.Phone.data: Map<String, Any?>
    get() = mapOf("number" to number, "type" to type)

val Barcode.Sms.data: Map<String, Any?>
    get() = mapOf("message" to message, "phoneNumber" to phoneNumber)

val Barcode.UrlBookmark.data: Map<String, Any?>
    get() = mapOf("title" to title, "url" to url)

val Barcode.WiFi.data: Map<String, Any?>
    get() = mapOf("encryptionType" to encryptionType, "password" to password, "ssid" to ssid)