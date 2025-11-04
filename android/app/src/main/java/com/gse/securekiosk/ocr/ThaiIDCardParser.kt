package com.gse.securekiosk.ocr

import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * ThaiIDCardParser - วิเคราะห์บัตรประชาชนไทย
 * 
 * Features:
 * - Extract เลขบัตรประชาชน (13 หลัก)
 * - Extract ชื่อ-นามสกุล (ภาษาไทยและอังกฤษ)
 * - Extract วันเกิด, วันหมดอายุ
 * - Extract ที่อยู่
 * - Validate ข้อมูลที่ได้
 * - Confidence scoring
 */
object ThaiIDCardParser {
    private const val TAG = "ThaiIDCardParser"
    
    data class IDCardData(
        val idNumber: String = "",
        val titleTh: String = "",
        val firstNameTh: String = "",
        val lastNameTh: String = "",
        val titleEn: String = "",
        val firstNameEn: String = "",
        val lastNameEn: String = "",
        val birthDate: String = "",
        val issueDate: String = "",
        val expiryDate: String = "",
        val address: String = "",
        val religion: String = "",
        val confidence: Float = 0f,
        val isValid: Boolean = false,
        val errors: List<String> = emptyList()
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("idNumber", idNumber)
                put("titleTh", titleTh)
                put("firstNameTh", firstNameTh)
                put("lastNameTh", lastNameTh)
                put("titleEn", titleEn)
                put("firstNameEn", firstNameEn)
                put("lastNameEn", lastNameEn)
                put("birthDate", birthDate)
                put("issueDate", issueDate)
                put("expiryDate", expiryDate)
                put("address", address)
                put("religion", religion)
                put("confidence", confidence)
                put("isValid", isValid)
                put("errors", org.json.JSONArray(errors))
            }.toString()
        }
    }
    
    /**
     * Parse OCR text to extract ID card data
     */
    fun parse(ocrText: String): IDCardData {
        Log.d(TAG, "Parsing Thai ID card...")
        Log.d(TAG, "Raw OCR text: $ocrText")
        
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        
        // Extract fields
        val idNumber = extractIDNumber(lines)
        val names = extractNames(lines)
        val dates = extractDates(lines)
        val address = extractAddress(lines)
        val religion = extractReligion(lines)
        
        // Calculate confidence
        val confidence = calculateConfidence(idNumber, names, dates)
        
        // Validate
        val errors = validate(idNumber, names, dates)
        val isValid = errors.isEmpty()
        
        return IDCardData(
            idNumber = idNumber,
            titleTh = names["titleTh"] ?: "",
            firstNameTh = names["firstNameTh"] ?: "",
            lastNameTh = names["lastNameTh"] ?: "",
            titleEn = names["titleEn"] ?: "",
            firstNameEn = names["firstNameEn"] ?: "",
            lastNameEn = names["lastNameEn"] ?: "",
            birthDate = dates["birthDate"] ?: "",
            issueDate = dates["issueDate"] ?: "",
            expiryDate = dates["expiryDate"] ?: "",
            address = address,
            religion = religion,
            confidence = confidence,
            isValid = isValid,
            errors = errors
        )
    }
    
    /**
     * Extract ID Number (13 digits)
     */
    private fun extractIDNumber(lines: List<String>): String {
        // Pattern: 1-2345-67890-12-3 or 1234567890123
        val patterns = listOf(
            Regex("""(\d)[\s\-]*(\d{4})[\s\-]*(\d{5})[\s\-]*(\d{2})[\s\-]*(\d)"""),
            Regex("""(\d{13})"""),
            Regex("""เลขประจำตัวประชาชน[\s:]*(\d[\s\-]*\d{4}[\s\-]*\d{5}[\s\-]*\d{2}[\s\-]*\d)"""),
            Regex("""Identification\s+Number[\s:]*(\d[\s\-]*\d{4}[\s\-]*\d{5}[\s\-]*\d{2}[\s\-]*\d)""")
        )
        
        for (line in lines) {
            for (pattern in patterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val digits = match.value.replace(Regex("""[^\d]"""), "")
                    if (digits.length == 13 && isValidIDNumber(digits)) {
                        Log.d(TAG, "Found ID number: $digits")
                        return digits
                    }
                }
            }
        }
        
        return ""
    }
    
    /**
     * Validate ID number using Thai ID card algorithm
     */
    private fun isValidIDNumber(idNumber: String): Boolean {
        if (idNumber.length != 13) return false
        
        try {
            var sum = 0
            for (i in 0..11) {
                sum += idNumber[i].toString().toInt() * (13 - i)
            }
            val checkDigit = (11 - (sum % 11)) % 10
            return checkDigit == idNumber[12].toString().toInt()
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Extract Names (Thai and English)
     */
    private fun extractNames(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        // Thai titles
        val thaiTitles = listOf("นาย", "นาง", "นางสาว", "เด็กชาย", "เด็กหญิง")
        val englishTitles = listOf("Mr.", "Mrs.", "Miss", "Master")
        
        // Find Thai name
        for (i in lines.indices) {
            val line = lines[i]
            
            // Check for Thai name pattern
            for (title in thaiTitles) {
                if (line.contains(title)) {
                    val parts = line.replace(Regex("""[^\u0E00-\u0E7F\s]"""), "").trim().split(Regex("""\s+"""))
                    if (parts.size >= 3) {
                        result["titleTh"] = parts[0]
                        result["firstNameTh"] = parts[1]
                        result["lastNameTh"] = parts.drop(2).joinToString(" ")
                        Log.d(TAG, "Found Thai name: ${parts.joinToString(" ")}")
                    }
                    break
                }
            }
            
            // Check for English name pattern
            for (title in englishTitles) {
                if (line.contains(title)) {
                    val parts = line.replace(Regex("""[^a-zA-Z.\s]"""), "").trim().split(Regex("""\s+"""))
                    if (parts.size >= 3) {
                        result["titleEn"] = parts[0]
                        result["firstNameEn"] = parts[1]
                        result["lastNameEn"] = parts.drop(2).joinToString(" ")
                        Log.d(TAG, "Found English name: ${parts.joinToString(" ")}")
                    }
                    break
                }
            }
        }
        
        return result
    }
    
    /**
     * Extract Dates (Birth, Issue, Expiry)
     */
    private fun extractDates(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        // Date patterns (DD MM YYYY or DD/MM/YYYY or DD-MM-YYYY)
        val datePattern = Regex("""(\d{1,2})[\s/\-\.]+(\d{1,2}|[ก-ฮ]+\.?)[\s/\-\.]+(\d{4})""")
        
        val thaiMonths = mapOf(
            "ม.ค." to "01", "ก.พ." to "02", "มี.ค." to "03", "เม.ย." to "04",
            "พ.ค." to "05", "มิ.ย." to "06", "ก.ค." to "07", "ส.ค." to "08",
            "ก.ย." to "09", "ต.ค." to "10", "พ.ย." to "11", "ธ.ค." to "12"
        )
        
        for (line in lines) {
            // Birth date
            if (line.contains("เกิด") || line.contains("Date of Birth")) {
                val match = datePattern.find(line)
                if (match != null) {
                    val (day, month, year) = match.destructured
                    val normalizedMonth = thaiMonths[month] ?: month.padStart(2, '0')
                    var normalizedYear = year
                    
                    // Convert Buddhist year to Christian year
                    if (normalizedYear.toInt() > 2400) {
                        normalizedYear = (normalizedYear.toInt() - 543).toString()
                    }
                    
                    result["birthDate"] = "${day.padStart(2, '0')}/${normalizedMonth}/${normalizedYear}"
                    Log.d(TAG, "Found birth date: ${result["birthDate"]}")
                }
            }
            
            // Issue date
            if (line.contains("วันออกบัตร") || line.contains("Date of Issue")) {
                val match = datePattern.find(line)
                if (match != null) {
                    val (day, month, year) = match.destructured
                    val normalizedMonth = thaiMonths[month] ?: month.padStart(2, '0')
                    var normalizedYear = year
                    
                    if (normalizedYear.toInt() > 2400) {
                        normalizedYear = (normalizedYear.toInt() - 543).toString()
                    }
                    
                    result["issueDate"] = "${day.padStart(2, '0')}/${normalizedMonth}/${normalizedYear}"
                    Log.d(TAG, "Found issue date: ${result["issueDate"]}")
                }
            }
            
            // Expiry date
            if (line.contains("วันบัตรหมดอายุ") || line.contains("Date of Expiry")) {
                val match = datePattern.find(line)
                if (match != null) {
                    val (day, month, year) = match.destructured
                    val normalizedMonth = thaiMonths[month] ?: month.padStart(2, '0')
                    var normalizedYear = year
                    
                    if (normalizedYear.toInt() > 2400) {
                        normalizedYear = (normalizedYear.toInt() - 543).toString()
                    }
                    
                    result["expiryDate"] = "${day.padStart(2, '0')}/${normalizedMonth}/${normalizedYear}"
                    Log.d(TAG, "Found expiry date: ${result["expiryDate"]}")
                }
            }
        }
        
        return result
    }
    
    /**
     * Extract Address
     */
    private fun extractAddress(lines: List<String>): String {
        val addressKeywords = listOf("ที่อยู่", "Address")
        var collectingAddress = false
        val addressLines = mutableListOf<String>()
        
        for (line in lines) {
            if (addressKeywords.any { line.contains(it) }) {
                collectingAddress = true
                // Add current line without keyword
                val cleaned = addressKeywords.fold(line) { acc, keyword -> 
                    acc.replace(keyword, "").trim()
                }
                if (cleaned.isNotEmpty()) {
                    addressLines.add(cleaned)
                }
                continue
            }
            
            if (collectingAddress) {
                // Stop if we hit another section
                if (line.contains("เลขประจำตัว") || 
                    line.contains("เกิด") || 
                    line.contains("ออกบัตร") ||
                    line.length > 100) {
                    break
                }
                addressLines.add(line)
            }
        }
        
        return addressLines.joinToString(" ").take(200) // Limit to 200 chars
    }
    
    /**
     * Extract Religion
     */
    private fun extractReligion(lines: List<String>): String {
        val religions = listOf("พุทธ", "อิสลาม", "คริสต์", "ฮินดู", "ซิกข์")
        
        for (line in lines) {
            if (line.contains("ศาสนา") || line.contains("Religion")) {
                for (religion in religions) {
                    if (line.contains(religion)) {
                        return religion
                    }
                }
            }
        }
        
        return ""
    }
    
    /**
     * Calculate confidence score
     */
    private fun calculateConfidence(
        idNumber: String,
        names: Map<String, String>,
        dates: Map<String, String>
    ): Float {
        var score = 0f
        
        // ID number (40%)
        if (idNumber.isNotEmpty() && isValidIDNumber(idNumber)) {
            score += 0.4f
        }
        
        // Thai name (30%)
        if (names.containsKey("firstNameTh") && names["firstNameTh"]!!.isNotEmpty()) {
            score += 0.15f
        }
        if (names.containsKey("lastNameTh") && names["lastNameTh"]!!.isNotEmpty()) {
            score += 0.15f
        }
        
        // Birth date (20%)
        if (dates.containsKey("birthDate") && dates["birthDate"]!!.isNotEmpty()) {
            score += 0.2f
        }
        
        // Expiry date (10%)
        if (dates.containsKey("expiryDate") && dates["expiryDate"]!!.isNotEmpty()) {
            score += 0.1f
        }
        
        return score
    }
    
    /**
     * Validate extracted data
     */
    private fun validate(
        idNumber: String,
        names: Map<String, String>,
        dates: Map<String, String>
    ): List<String> {
        val errors = mutableListOf<String>()
        
        if (idNumber.isEmpty()) {
            errors.add("ไม่พบเลขบัตรประชาชน")
        } else if (!isValidIDNumber(idNumber)) {
            errors.add("เลขบัตรประชาชนไม่ถูกต้อง")
        }
        
        if (!names.containsKey("firstNameTh") || names["firstNameTh"]!!.isEmpty()) {
            errors.add("ไม่พบชื่อ (ภาษาไทย)")
        }
        
        if (!names.containsKey("lastNameTh") || names["lastNameTh"]!!.isEmpty()) {
            errors.add("ไม่พบนามสกุล (ภาษาไทย)")
        }
        
        if (!dates.containsKey("birthDate") || dates["birthDate"]!!.isEmpty()) {
            errors.add("ไม่พบวันเกิด")
        }
        
        return errors
    }
}

