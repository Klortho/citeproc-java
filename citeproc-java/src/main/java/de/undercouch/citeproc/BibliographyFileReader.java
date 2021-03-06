// Copyright 2014 Michel Kraemer
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package de.undercouch.citeproc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jbibtex.BibTeXDatabase;
import org.jbibtex.ParseException;

import de.undercouch.citeproc.bibtex.BibTeXConverter;
import de.undercouch.citeproc.bibtex.BibTeXItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.endnote.EndNoteConverter;
import de.undercouch.citeproc.endnote.EndNoteItemDataProvider;
import de.undercouch.citeproc.endnote.EndNoteLibrary;
import de.undercouch.citeproc.helper.json.JsonLexer;
import de.undercouch.citeproc.helper.json.JsonParser;
import de.undercouch.citeproc.ris.RISConverter;
import de.undercouch.citeproc.ris.RISItemDataProvider;
import de.undercouch.citeproc.ris.RISLibrary;

/**
 * A convenience class providing methods to load any supported kind of
 * bibliography files. The class automatically detects the correct file
 * format and returns a {@link ItemDataProvider} that holds all
 * bibliography items read from the file.
 * @author Michel Kraemer
 */
public class BibliographyFileReader {
	/**
	 * Supported file formats for bibliography files
	 */
	public static enum FileFormat {
		/**
		 * A BibTeX file
		 */
		BIBTEX,
		
		/**
		 * A CSL JSON object
		 */
		JSON_OBJECT,
		
		/**
		 * An array of CSL JSON objects
		 */
		JSON_ARRAY,
		
		/**
		 * An EndNote file
		 */
		ENDNOTE,
		
		/**
		 * An RIS file
		 */
		RIS,
		
		/**
		 * Unknown file format
		 */
		UNKNOWN
	}
	
	/**
	 * Reads all items from an input bibliography file and returns a provider
	 * serving these items
	 * @param bibfile the input file
	 * @return the provider
	 * @throws FileNotFoundException if the input file was not found
	 * @throws IOException if the input file could not be read
	 */
	public ItemDataProvider readBibliographyFile(File bibfile)
			throws FileNotFoundException, IOException {
		//open buffered input stream to bibliography file
		if (!bibfile.exists()) {
			throw new FileNotFoundException("Bibliography file `" + 
					bibfile.getName() + "' does not exist");
		}
		BufferedInputStream bis = new BufferedInputStream(
				new FileInputStream(bibfile));
		try {
			return readBibliographyFile(bis, bibfile.getName());
		} finally {
			bis.close();
		}
	}
	
	/**
	 * Reads all items from an input stream and returns a provider
	 * serving these items. Note that you can supply an additional file
	 * name to help the method to determine the exact bibliography file format.
	 * If you don't know the file name you can pass null, but in this case the
	 * method's result might try to read the input stream using the wrong
	 * file format (depending on the input stream's contents). Also note
	 * that the caller is responsible for closing the given input stream.
	 * @param bibstream the input stream
	 * @param filename the name of the input file (can be null if you don't
	 * know the name)
	 * @return the provider
	 * @throws IOException if the input stream could not be read
	 */
	public ItemDataProvider readBibliographyFile(InputStream bibstream,
			String filename) throws IOException {
		BufferedInputStream bis;
		if (bibstream instanceof BufferedInputStream) {
			bis = (BufferedInputStream)bibstream;
		} else {
			bis = new BufferedInputStream(bibstream);
		}
		
		//determine file format
		FileFormat ff = determineFileFormat(bis, filename);
		
		//read stream
		return readBibliographyFile(bis, ff);
	}
	
	/**
	 * Reads all items from an input stream using the given file format and
	 * returns a provider serving these items.
	 * @param bibstream the input stream
	 * @param format the bibliography file format 
	 * @return the provider
	 * @throws IOException if the input stream could not be read
	 */
	public ItemDataProvider readBibliographyFile(InputStream bibstream,
			FileFormat format) throws IOException {
		ItemDataProvider provider;
		try {
			//load bibliography file
			if (format == FileFormat.BIBTEX) {
				BibTeXDatabase db = new BibTeXConverter().loadDatabase(bibstream);
				BibTeXItemDataProvider bibtexprovider = new BibTeXItemDataProvider();
				bibtexprovider.addDatabase(db);
				provider = bibtexprovider;
			} else if (format == FileFormat.JSON_ARRAY ||
					format == FileFormat.JSON_OBJECT) {
				JsonParser parser = new JsonParser(new JsonLexer(
						new InputStreamReader(bibstream)));
				List<Object> objs;
				if (format == FileFormat.JSON_ARRAY) {
					objs = parser.parseArray();
				} else {
					objs = new ArrayList<Object>();
					objs.add(parser.parseObject());
				}
				CSLItemData[] items = new CSLItemData[objs.size()];
				for (int i = 0; i < items.length; ++i) {
					@SuppressWarnings("unchecked")
					Map<String, Object> obj = (Map<String, Object>)objs.get(i);
					items[i] = CSLItemData.fromJson(obj);
				}
				provider = new ListItemDataProvider(items);
			} else if (format == FileFormat.ENDNOTE) {
				EndNoteLibrary lib = new EndNoteConverter().loadLibrary(bibstream);
				EndNoteItemDataProvider endnoteprovider = new EndNoteItemDataProvider();
				endnoteprovider.addLibrary(lib);
				provider = endnoteprovider;
			} else if (format == FileFormat.RIS) {
				RISLibrary lib = new RISConverter().loadLibrary(bibstream);
				RISItemDataProvider risprovider = new RISItemDataProvider();
				risprovider.addLibrary(lib);
				provider = risprovider;
			} else {
				throw new IOException("Unknown bibliography file format");
			}
		} catch (ParseException e) {
			throw new IOException("Could not parse bibliography file", e);
		}
		
		return provider;
	}
	
	/**
	 * Reads the first 100 KB of the given bibliography file and tries
	 * to determine the file format
	 * @param bibfile the input file
	 * @return the file format (or {@link FileFormat#UNKNOWN} if the format
	 * could not be determined)
	 * @throws FileNotFoundException if the input file was not found
	 * @throws IOException if the input file could not be read
	 */
	public FileFormat determineFileFormat(File bibfile)
			throws FileNotFoundException, IOException {
		if (!bibfile.exists()) {
			throw new FileNotFoundException("Bibliography file `" + 
					bibfile.getName() + "' does not exist");
		}
		BufferedInputStream bis = new BufferedInputStream(
				new FileInputStream(bibfile));
		try {
			return determineFileFormat(bis, bibfile.getName());
		} finally {
			bis.close();
		}
	}
	
	/**
	 * Reads the first bytes of the given input stream and tries to
	 * determine the file format. Resets the input stream to the position
	 * it had when the method was called. Reads up to 100 KB and before
	 * giving up. Note that you can supply an additional file name to help
	 * the method to determine the exact file format. If you don't know the
	 * file name you can pass null, but in this case the method's result
	 * might be wrong (depending on the input stream's contents).
	 * @param bis a buffered input stream that supports the mark and reset
	 * methods
	 * @param filename the name of the input file (can be null if you don't
	 * know the name)
	 * @return the file format (or {@link FileFormat#UNKNOWN} if the format
	 * could not be determined)
	 * @throws IOException if the input stream could not be read
	 */
	public FileFormat determineFileFormat(BufferedInputStream bis,
			String filename) throws IOException {
		int len = 1024 * 100;
		
		String ext = "";
		if (filename != null) {
			int dot = filename.lastIndexOf('.');
			if (dot > 0) {
				ext = filename.substring(dot + 1);
			}
		}
		
		//check if it's an EndNote library
		bis.mark(len);
		try {
			byte[] firstCharacters = new byte[5];
			bis.read(firstCharacters);
			
			//check if the file starts with an EndNote tag, but
			//also make sure the extension is not 'bib' because
			//BibTeX comments look like EndNote tags
			if (firstCharacters[0] == '%' &&
					Character.isWhitespace(firstCharacters[2]) &&
					!ext.equalsIgnoreCase("bib")) {
				return FileFormat.ENDNOTE;
			}
			
			//check if the file starts with a RIS type tag
			if (firstCharacters[0] == 'T' && firstCharacters[1] == 'Y' &&
					Character.isWhitespace(firstCharacters[2]) &&
					Character.isWhitespace(firstCharacters[3]) &&
					firstCharacters[4] == '-') {
				return FileFormat.RIS;
			}
		} finally {
			bis.reset();
		}
		
		//now check if it's json or bibtex
		bis.mark(len);
		try {
			while (true) {
				int c = bis.read();
				--len;
				if (c < 0 || len < 2) {
					return FileFormat.UNKNOWN;
				}
				
				if (!Character.isWhitespace(c)) {
					if (c == '[') {
						return FileFormat.JSON_ARRAY;
					} else if (c == '{') {
						return FileFormat.JSON_OBJECT;
					}
					return FileFormat.BIBTEX;
				}
			}
		} finally {
			bis.reset();
		}
	}
}
