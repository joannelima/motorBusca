import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XMLReader {
    public static void main(String[] args) {
        try {
            File inputFile = new File("src/verbetesWikipedia.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            NodeList pageList = doc.getElementsByTagName("page");
            for (int i = 0; i < pageList.getLength(); i++) {
                NodeList wordList = pageList.item(i).getChildNodes();
                for (int j = 0; j < wordList.getLength(); j++) {
                    if (wordList.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        System.out.println(wordList.item(j).getTextContent());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

