package lib.lenovo;

/**
 Copyright 2005 Bytecode Pty Ltd.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A very simple CSV reader released under a commercial-friendly license.
 *
 * @author Glen Smith
 *
 * ---------------------------------------------------------------------------------------
 * Modification History
 * ---------------------------------------------------------------------------------------
 * 2011-06-10 C4359 MM version 1.3.11 - Add support for Excel file: implements new interface IFileReader
 */
public class CSVReader implements IFileReader{

	private BufferedReader br;
	private boolean hasNext = true;
	private char separator;
	private char quotechar;
	private String currentLine;						//Added by Victor Monaco IBM

	/** The default separator to use if none is supplied to the constructor. */
	public static final char DEFAULT_SEPARATOR = ',';

	/** The default quote character to use if none is supplied to the constructor. */
	public static final char DEFAULT_QUOTE_CHARACTER = '"';

	/**
	 * Constructs CSVReader using a comma for the separator.
	 *
	 * @param reader
	 *            the reader to an underlying CSV source.
	 */
	public CSVReader(Reader reader) {
		this(reader, DEFAULT_SEPARATOR);
	}

	/**
	 * Constructs CSVReader with supplied separator.
	 *
	 * @param reader the reader to an underlying CSV source.
	 * @param separator the delimiter to use for separating entries.
	 */
	public CSVReader(Reader reader, char separator) {
		this(reader, separator, DEFAULT_QUOTE_CHARACTER);
	}

	/**
	 * Constructs CSVReader with supplied separator and quote char.
	 *
	 * @param reader the reader to an underlying CSV source.
	 * @param separator the delimiter to use for separating entries
	 * @param quotechar the character to use for quoted elements
	 */
	public CSVReader(Reader reader, char separator, char quotechar) {
		this.br = new BufferedReader(reader);
		this.separator = separator;
		this.quotechar = quotechar;
	}

	/**
	 * Reads the entire file into a List with each element being a String[] of
	 * tokens.
	 *
	 * @return a List of String[], with each String[] representing a line of the
	 *         file.
	 *
	 * @throws IOException
	 *             if bad things happen during the read
	 */
	public List readAll() throws IOException {

		List allElements = new ArrayList();
		while (hasNext) {
			String[] nextLineAsTokens = readNext();
			if (nextLineAsTokens != null)
				allElements.add(nextLineAsTokens);
		}
		return allElements;

	}

	/**
	 * Reads the next line from the buffer and converts to a string array.
	 *
	 * @return a string array with each comma-separated element as a separate
	 *         entry.
	 *
	 * @throws IOException
	 *             if bad things happen during the read
	 */
	public String[] readNext() throws IOException {

		String nextLine = getNextLine();
		this.currentLine = nextLine;					//Added by Victor Monaco IBM
		return hasNext ? parseLine(nextLine) : null;
	}


	//Method Added by Victor Monaco - Millennium IT
	/**
	 * Sends back the current raw data input line to the calling program
	 *
	 * @return a string in it's original input form
	 *
	 */
	public String currentLine() {
		return this.currentLine;
	}

	//Method Added by Victor Monaco IBM - Millennium IT
	/**
	 * Closes the current input stream so the file does not remain open.
	 *
	 * @return boolean true if the close was successfull, false otherwise.
	 *
	 */
	public boolean close() {
		boolean result;
		try {
			this.br.close();
			result = true;
		} catch (IOException io) {
			result = false;
		}

	return(result);
	}



	/**
	 * Reads the next line from the file.
	 *
	 * @return the next line from the file without trailing newline
	 * @throws IOException if bad things happen during the read
	 */
	private String getNextLine() throws IOException {
		String nextLine = br.readLine();
		if (nextLine == null) {
			hasNext = false;
		}
		return hasNext ? nextLine : null;
	}

	/**
	 * Parses an incoming String and returns an array of elements.
	 *
	 * @param nextLine
	 *            the string to parse
	 * @return the comma-tokenized list of elements, or null if nextLine is null
	 * @throws IOException
	 */
	private String[] parseLine(String nextLine) throws IOException {

		if (nextLine == null) {
			return null;
		}

		List tokensOnThisLine = new ArrayList();
		StringBuffer sb = new StringBuffer();
		boolean inQuotes = false;
		do {
			if (sb.length() > 0) {
				// continuing a quoted section, reappend newline
				sb.append("\n");
				nextLine = getNextLine();
				if (nextLine == null)
					break;
			}
			for (int i = 0; i < nextLine.length(); i++) {

				char c = nextLine.charAt(i);
				if (c == quotechar) {
					inQuotes = !inQuotes;
				} else if (c == separator && !inQuotes) {
					tokensOnThisLine.add(sb.toString());
					sb = new StringBuffer(); // start work on next token
				} else {
					sb.append(c);
				}
			}
		} while (inQuotes);
		tokensOnThisLine.add(sb.toString());
		return (String[]) tokensOnThisLine.toArray(new String[0]);

	}

}
