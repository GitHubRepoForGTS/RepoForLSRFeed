package lib.lenovo;

import java.io.IOException;
import java.util.List;

public interface IFileReader {
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
	public List readAll() throws IOException;
	/**
	 * Reads the next line from the buffer and converts to a string array.
	 *
	 * @return a string array with each comma-separated element as a separate
	 *         entry.
	 *
	 * @throws IOException
	 *             if bad things happen during the read
	 */
	public String[] readNext()throws IOException;
	/**
	 * Sends back the current raw data input line to the calling program
	 *
	 * @return a string in it's original input form
	 *
	 */
	public String currentLine();
	/**
	 * Closes the current input stream so the file does not remain open.
	 *
	 * @return boolean true if the close was successfull, false otherwise.
	 *
	 */
	public boolean close();
	
}
