package com.tesseract.api.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tesseract.api.intercept.AppProperties
import com.tesseract.api.model.*
import net.sourceforge.tess4j.Tesseract
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Paths
import java.time.*
import java.util.*

@Service
class TesseactService
{
    @Autowired
    lateinit var appProperties: AppProperties

    val mapper = jacksonObjectMapper()

    /**
     * Get a List of TesseractLanguage of languages installed on server
     * @param none
     * @return List<TesseractLanguage>
     * @throws none
     **/
    fun getInstalledLanguages(): List<TesseractLanguage>
    {
        val langAllJson = this::class.java.classLoader.getResource(appProperties.languagesFile)?.readText(Charsets.UTF_8)
        val langAll = mapper.readValue<ArrayList<TesseractLanguage>>(langAllJson.orEmpty())
        return langAll.filter { l -> appProperties.installedLanguages.contains(l.key!!) }
    }

    /**
     * Get TesseractLanguage from three letter key
     * @param key String three letter language key
     * @return TesseractLanguage or null
     * @throws none
     **/
    fun getTesseractLanguage(key: String): TesseractLanguage?
    {
        if(key.length != 3)
            return null

        val langAllJson = this::class.java.classLoader.getResource(appProperties.languagesFile)?.readText(Charsets.UTF_8)
        val langAll = mapper.readValue<ArrayList<TesseractLanguage>>(langAllJson.orEmpty())
        return langAll.find { l -> key == l.key!! }
    }

    /**
     * Process image from base64 and get the text in it
     * @param base64 String encoded as base64
     * @param lang TesseractLanguage to use when reading
     * @return TesseractResult
     * @throws none
     **/
    fun processImage(
        base64: String, lang: TesseractLanguage,
        pageSeg: PageSegmentationMode,
        engineMode: OcrEngineMode): TesseractResult
    {
        val imageBytes = Base64.getDecoder().decode(base64.base64FileContent())
        return processImage(imageBytes, base64.base64FileExtension(), lang, pageSeg, engineMode)
    }

    /**
     * Process image from ByteArray and get the text in it
     * @param bytes ByteArray of image content
     * @param fileExtension String? file extension without .
     * @param lang TesseractLanguage to use when reading
     * @return TesseractResult
     * @throws none
     **/
    fun processImage(bytes: ByteArray, fileExtension: String?, lang: TesseractLanguage,
                     pageSeg: PageSegmentationMode, engineMode: OcrEngineMode): TesseractResult
    {
        mkdirIfNone(appProperties.tempDir)

        val extension = fileExtension ?: bytes.guessMimeType().split("/").last()
        val tempFilename = getTempFilename(extension)
        File(tempFilename).writeBytes(bytes)

        val file = File(tempFilename)
        val rawText = readImage(file, lang, pageSeg, engineMode)
        file.delete()

        return TesseractResult(rawText, rawText.cleanString(), lang)
    }

    /**
     * Get the text of an image
     * @param file File image file
     * @param lang TesseractLanguage to use when reading
     * @return String
     * @throws none
     **/
    fun readImage(file: File, lang: TesseractLanguage,
                  pageSeg: PageSegmentationMode, engineMode: OcrEngineMode): String
    {
        val tesseract = Tesseract()
        tesseract.setLanguage((lang.key))
        tesseract.setDatapath(appProperties.installFullPath)
        tesseract.setPageSegMode(pageSeg.ordinal)
        tesseract.setOcrEngineMode(engineMode.ordinal)
//        tesseract.setTessVariable("user_defined_dpi", appProperties.userDefinedDpi) // Optional, Tesseract will approximate DPI and resolution

        return tesseract.doOCR(file)
    }

    /**
     * Get temporary absolute filename of DateTime without -:.T
     * @param extension String file extension without .
     * @return String
     * @throws none
     **/
    fun getTempFilename(extension: String): String
    {
        return Paths.get(appProperties.tempDir, LocalDateTime.now()
            .toString()
            .replace("-", "")
            .replace(":", "")
            .replace(".", "")
            .replace("T", "")
                + ".${extension}")
            .toAbsolutePath()
            .toString()
    }

    /**
     * Make directory path if it does not exist.
     * @param path String path, relative or absolute
     * @return Boolean result of making file, true if made
     * @throws none
     **/
    fun mkdirIfNone(path: String): Boolean
    {
        if(!File(path).exists())
            return File(path).mkdir()

        return false
    }
}