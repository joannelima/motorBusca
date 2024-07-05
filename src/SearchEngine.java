import java.io.File;
import java.util.Scanner;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

public class SearchEngine {
    private Map<String, Map<Integer, Integer>> wordPageCountMap; // Hash invertido: palavra -> (página -> contagem)
    private LinkedHashMap<String, List<Map.Entry<Integer, Integer>>> cache; //lista encadeada
    private final int CACHE_SIZE = 5; // cache

    public SearchEngine(String xmlFilePath) {
        wordPageCountMap = new HashMap<>();
        cache = new LinkedHashMap<String, List<Map.Entry<Integer, Integer>>>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Map.Entry<Integer, Integer>>> eldest) {
                return size() > CACHE_SIZE;
            }
        };
        loadWordsFromXML(xmlFilePath);
    }

    private void loadWordsFromXML(String xmlFilePath) {
        try {
            File inputFile = new File(xmlFilePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            NodeList pageList = doc.getElementsByTagName("page");
            for (int i = 0; i < pageList.getLength(); i++) {
                Element pageElement = (Element) pageList.item(i);
                int pageId = Integer.parseInt(pageElement.getElementsByTagName("id").item(0).getTextContent());

                // Processar título
                String titleContent = pageElement.getElementsByTagName("title").item(0).getTextContent().toLowerCase();
                Map<String, Integer> titleWordCounts = countWords(titleContent);

                // Processar texto
                String textContent = pageElement.getElementsByTagName("text").item(0).getTextContent().toLowerCase();
                Map<String, Integer> textWordCounts = countWords(textContent);

                // Mesclar contagens e multiplicar por 2 se a palavra estiver no título
                for (Map.Entry<String, Integer> entry : textWordCounts.entrySet()) {
                    String word = entry.getKey();
                    int count = entry.getValue();
                    if (titleWordCounts.containsKey(word)) {
                        count += titleWordCounts.get(word);
                        count *= 2;  // Multiplica o total de ocorrências por 2
                    }
                    addWord(word, pageId, count);
                }

                // Adicionar palavras que estão apenas no título
                for (Map.Entry<String, Integer> entry : titleWordCounts.entrySet()) {
                    String word = entry.getKey();
                    if (!textWordCounts.containsKey(word)) {
                        addWord(word, pageId, entry.getValue() * 2);  // Multiplica as ocorrências do título por 2
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, Integer> countWords(String content) {
        Map<String, Integer> wordCounts = new HashMap<>();
        // Divide o conteúdo em palavras usando \\s+ para lidar com qualquer número de espaços
        String[] words = content.split("\\s+");
        for (String word : words) {
            // Considera apenas palavras com mais de 4 caracteres
            if (word.length() > 3) {
                wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
            }
        }
        return wordCounts;
    }

    private void addWord(String word, int pageIndex, int count) {
        wordPageCountMap.putIfAbsent(word, new HashMap<>());
        Map<Integer, Integer> pageCountMap = wordPageCountMap.get(word);
        pageCountMap.put(pageIndex, pageCountMap.getOrDefault(pageIndex, 0) + count);
    }

    public List<Map.Entry<Integer, Integer>> search(String[] queries) {
        String key = String.join(" ", queries) + " AND"; // Cria uma chave para o cache

        if (cache.containsKey(key)) {
            System.out.println("Resultado encontrado no cache.");
            return cache.get(key);
        }

        Map<Integer, Integer> combinedResults = new HashMap<>();

        if (queries.length == 0) return Collections.emptyList();

        // Busca a primeira palavra
        String firstQuery = queries[0].toLowerCase();
        Map<Integer, Integer> pageCountMap = new HashMap<>(wordPageCountMap.getOrDefault(firstQuery, Collections.emptyMap()));

        // Interseção das páginas com todas as palavras
        for (int i = 1; i < queries.length; i++) {
            String query = queries[i].toLowerCase();
            Map<Integer, Integer> currentMap = wordPageCountMap.getOrDefault(query, Collections.emptyMap());

            // Atualiza o map com a interseção das páginas
            pageCountMap.keySet().retainAll(currentMap.keySet());
            for (Map.Entry<Integer, Integer> entry : pageCountMap.entrySet()) {
                if (currentMap.containsKey(entry.getKey())) {
                    entry.setValue(Math.min(entry.getValue(), currentMap.get(entry.getKey())));
                } else {
                    pageCountMap.remove(entry.getKey());
                }
            }
        }

        if (pageCountMap.isEmpty()) {
            List<Map.Entry<Integer, Integer>> orResults = searchWithOr(queries);
            cache.put(String.join(" ", queries) + " OR", orResults);

            return orResults;
        }

        combinedResults = pageCountMap;

        List<Map.Entry<Integer, Integer>> pageList = new ArrayList<>(combinedResults.entrySet());
        pageList.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue())); // Ordena por contagem de ocorrências

        cache.put(key, pageList);

        return pageList;
    }

    private List<Map.Entry<Integer, Integer>> searchWithOr(String[] queries) {
        Map<Integer, Integer> combinedResults = new HashMap<>();

        for (String query : queries) {
            String queryLowerCase = query.toLowerCase();
            Map<Integer, Integer> pageCountMap = wordPageCountMap.getOrDefault(queryLowerCase, Collections.emptyMap());

            for (Map.Entry<Integer, Integer> entry : pageCountMap.entrySet()) {
                int pageIndex = entry.getKey();
                int count = entry.getValue();
                combinedResults.merge(pageIndex, count, Integer::sum);
            }
        }

        List<Map.Entry<Integer, Integer>> pageList = new ArrayList<>(combinedResults.entrySet());
        pageList.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue())); // Ordena por contagem de ocorrências

        return pageList;
    }

    public static void main(String[] args) {
        SearchEngine engine = new SearchEngine("src/verbetesWikipedia.xml");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Digite as palavras que deseja buscar (separadas por espaço), ou 'sair' para encerrar:");
            String input = scanner.nextLine().toLowerCase();

            if (input.equals("sair")) {
                break;
            }

            String[] queries = input.split("\\s+");
            List<Map.Entry<Integer, Integer>> results = engine.search(queries);

            // Mostra os 5 primeiros resultados da pesquisa
            if (!results.isEmpty()) {
                System.out.println("Top 5 páginas para '" + String.join(" ", queries) + "':");
                for (int i = 0; i < Math.min(5, results.size()); i++) {
                    Map.Entry<Integer, Integer> entry = results.get(i);
                    System.out.println("Página " + entry.getKey() + ": " + entry.getValue() + " ocorrências");
                }
            } else {
                System.out.println("Nenhum resultado foi encontrado para a sua pesquisa");
            }
        }

        scanner.close();
    }
}
