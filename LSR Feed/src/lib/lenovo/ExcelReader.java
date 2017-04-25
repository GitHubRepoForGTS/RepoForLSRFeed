package lib.lenovo;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;

/**
 * new Reader for LSR Feed to support MS Excel import file.
 * using open source jxl library version:Jcxcelapi 2.6.9.1.4 for (JDK 1.4)
 * download from: http://sourceforge.net/projects/jexcelapi/files/jexcelapi/
 * 
 * @author Mark Ma
 *
 * ---------------------------------------------------------------------------------------
 * Modification History
 * ---------------------------------------------------------------------------------------
 * 2011-06-10 C4359 MM  - initial release. Add support for Excel file: implements new interface IFileReader
 */
public class ExcelReader implements IFileReader{

	private String currentLine;	
	private Sheet workSheet;
	private int currentRow;
	private int colNum;
	
	/**
	 * Constructs ExcelReader 
	 *
	 * @param fileName
	 *            the file path and name to an underlying Excel file.
	 */
	public ExcelReader(String fileName) {
		File inputWorkbook = new File(fileName);
		Workbook w;
		try{
			w = Workbook.getWorkbook(inputWorkbook);
			// Get the first sheet
			this.workSheet = w.getSheet(0);
			currentRow = 0;
			colNum = workSheet.getColumns();
		}catch(Exception ex){
			this.workSheet = null;
		}
	}
	
	public boolean close() {
		
		if (workSheet != null){
			workSheet = null;
			return true;
		}else
			return false;
	}

	public String currentLine() {
		return currentLine;
	}


	public List readAll() {
		// TODO Auto-generated method stub
		return null;
	}

	public String[] readNext() {
		Cell[] cells = null;
		List list = new ArrayList();
		String  tempStr = null;
		StringBuffer sb = new StringBuffer();
		if (currentRow < workSheet.getRows()){
			cells = workSheet.getRow(currentRow++);
			for(int i=0;i<cells.length;i++){
				tempStr = cells[i].getContents();
				list.add(tempStr);
				sb.append(tempStr);
				sb.append(",");
			}
			//if last n cells is empty then append empty item
			if(cells.length<colNum){
				for(int i=0;i<colNum-cells.length;i++){
					sb.append(",");
					list.add("");
				}			
			}
			currentLine = sb.toString();
			int len = currentLine.length();
			currentLine = currentLine.substring(0,len-1);
		}
		if(tempStr==null){
			return null;
		}else
			return (String[]) list.toArray(new String[list.size()]);	
		}	
	}
