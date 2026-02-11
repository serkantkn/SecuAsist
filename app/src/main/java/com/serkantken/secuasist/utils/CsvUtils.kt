package com.serkantken.secuasist.utils

import com.serkantken.secuasist.models.Villa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvUtils {
    suspend fun parseVillasFromCsv(inputStream: InputStream): List<Villa> = withContext(Dispatchers.IO) {
        val villas = mutableListOf<Villa>()
        try {
            // Excel in Turkey usually exports as "windows-1254" (CP1254)
            val reader = BufferedReader(InputStreamReader(inputStream, "windows-1254"))
            val lines = reader.readLines()

            if (lines.isEmpty()) return@withContext emptyList()

            // 1. Detect Delimiter and BOM
            var firstLine = lines[0]
            // Remove BOM if present (UTF-8 BOM is \uFEFF)
            if (firstLine.isNotEmpty() && firstLine[0] == '\uFEFF') {
                firstLine = firstLine.substring(1)
            }

            // Determine separator based on first line
            val delimiter = if (firstLine.contains(";")) ";" else ","
            println("CSV Debug: Delimiter detected as '$delimiter'")

            // 2. Parse Lines
            var isFirstLine = true
            for (rawLine in lines) {
                var line = rawLine
                if (line.isEmpty()) continue
                
                // Remove BOM from the very first line again just in case loop starts from 0
                if (isFirstLine && line.isNotEmpty() && line[0] == '\uFEFF') {
                    line = line.substring(1)
                }

                // Split safely
                // Regex for splitting by delimiter but ignoring delimiter inside quotes
                // For comma: ,(?=(?:[^"]*"[^"]*")*[^"]*$)
                // For semicolon: ;(?=(?:[^"]*"[^"]*")*[^"]*$)
                val regex = "$delimiter(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()
                
                val parts = line.split(regex).map { 
                    it.trim().removeSurrounding("\"") 
                }

                // Header Check
                if (isFirstLine) {
                    isFirstLine = false
                    // If this looks like a header, skip it
                    if (parts.isNotEmpty()) {
                        val firstCell = parts[0].lowercase()
                        if (firstCell.contains("no") || firstCell.contains("villa")) {
                            println("CSV Debug: Skipping header line: $line")
                            continue
                        }
                    }
                    // If not a header, fall through to parse it as data
                }

                if (parts.isNotEmpty()) {
                    val villaNoStr = parts[0]
                    val villaNo = villaNoStr.toIntOrNull()

                    if (villaNo != null) {
                        val street = if (parts.size > 1) parts[1].takeIf { it.isNotEmpty() } else null
                        val notes = if (parts.size > 2) parts[2].takeIf { it.isNotEmpty() } else null
                        val navA = if (parts.size > 3) parts[3].takeIf { it.isNotEmpty() } else null
                        val navB = if (parts.size > 4) parts[4].takeIf { it.isNotEmpty() } else null

                        villas.add(Villa(
                            villaNo = villaNo,
                            villaStreet = street,
                            villaNotes = notes,
                            villaNavigationA = navA,
                            villaNavigationB = navB,
                            isVillaCallForCargo = 1
                        ))
                    } else {
                        println("CSV Debug: Could not parse villa number from: ${parts[0]}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        println("CSV Debug: Parsed ${villas.size} villas")
        return@withContext villas
    }
}
