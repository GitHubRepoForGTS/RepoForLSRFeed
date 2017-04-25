package lib.lenovo;
import java.io.BufferedReader;
import java.io.Reader;


/***************************************************************************************************
*  CLASS:  FlatFilesReader
*
*  PURPOSE:  The FlatFileDataReader class will process the fixed-width files line-by-line. 
*  Each line will be passed to an abstract class FileReader for you to parse out the column values 
*  and store them in variables.
*
*  CREATED BY:  Hang Shi  September, 2008 - IBM
*				
*
*  ---------------------------------------------------------------------------------------
*  Modification History
*  ---------------------------------------------------------------------------------------
*
***************************************************************************************************/


public class FlatFilesReader extends FilesReader{
	

	/**
	 * Constructs FlatFilesReader.
	 *
	 * @param reader the reader to an underlying flat file source.
	 */
	public FlatFilesReader(Reader reader) {
		
		this.br = new BufferedReader(reader);
		
	}

	
}
