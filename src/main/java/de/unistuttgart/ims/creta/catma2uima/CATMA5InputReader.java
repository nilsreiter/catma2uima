package de.unistuttgart.ims.creta.catma2uima;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.JCasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Multimaps;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import de.unistuttgart.ims.creta.catma2uima.types.CatmaAnnotation;
import de.unistuttgart.ims.creta.catma2uima.types.Seg;
import de.unistuttgart.ims.uima.io.xml.GenericXmlReader;

public class CATMA5InputReader extends JCasCollectionReader_ImplBase {

	public static final String PARAM_INPUT_DIRECTORY = "PARAM_INPUT_DIRECTORY";
	public static final String PARAM_FILE_SUFFIX = "PARAM_FILE_SUFFIX";
	public static final String PARAM_RECURSIVE = "PARAM_RECURSIVE";

	@ConfigurationParameter(name = PARAM_INPUT_DIRECTORY)
	String inputDirectoryName;

	@ConfigurationParameter(name = PARAM_FILE_SUFFIX, mandatory = false, defaultValue = ".xml")
	String fileSuffix;

	@ConfigurationParameter(name = PARAM_RECURSIVE, mandatory = false, defaultValue = "false")
	boolean recursive;

	ImmutableList<File> files;
	// File[] files;

	int current = 0;

	List<String> featureTypes;

	@Override
	public void initialize(final UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		File inputDirectory = new File(inputDirectoryName);
		if (!inputDirectory.isDirectory())
			throw new ResourceInitializationException("Input directory needs to be a directory ({0} is not ) ",
					new Object[] { inputDirectoryName });

		files = getFiles(inputDirectory, fileSuffix, recursive);
	}

	@Override
	public Progress[] getProgress() {
		return null;
	}

	@Override
	public boolean hasNext() throws IOException, CollectionException {
		return current < files.size();
	}

	@Override
	public void getNext(JCas jcas) throws IOException, CollectionException {
		File file = files.get(current++);
		try (FileInputStream fis = new FileInputStream(file)) {
			jcas = process(jcas, fis);
		} catch (UIMAException e) {
			throw new CollectionException(e);
		}
	}

	private JCas process(JCas jcas, InputStream is) throws UIMAException, IOException {

		MutableMap<String, String> fsId2typeId = Maps.mutable.empty();
		MutableMap<String, String> typeId2description = Maps.mutable.empty();
		MutableMap<String, String> propertiesMap = Maps.mutable.empty();

		/**
		 * This map contains annotation ids and the seg annotations they refer to
		 */
		MutableMultimap<String, Seg> annoMap = Multimaps.mutable.sortedSet.with(new Comparator<Seg>() {
			@Override
			public int compare(Seg o1, Seg o2) {
				return Integer.compare(o1.getBegin(), o2.getBegin());
			}
		});
		GenericXmlReader<DocumentAnnotation> reader;

		reader = new GenericXmlReader<DocumentAnnotation>(DocumentAnnotation.class);
		reader.setPreserveWhitespace(false);
		reader.setTextRootSelector("TEI > text > body");
		reader.addGlobalRule("fsDecl", (a, e) -> {
			String typeName = e.selectFirst("fsDescr").text();
			typeId2description.put(e.attr("xml:id"), typeName);
			// System.err.println("fsDecl: " + e.attr("xml:id") + ": " + typeName);

		});

		reader.addGlobalRule("fs", (a, e) -> {
			fsId2typeId.put(e.attr("xml:id"), e.attr("type"));
			// System.err.println("fs: " + e.attr("xml:id") + ": " + e.attr("type"));
			StringBuilder b = new StringBuilder();
			Elements propertyElements = e.select("f");
			for (int i = 0; i < propertyElements.size(); i++) {
				Element pElement = propertyElements.get(i);
				String name = pElement.attr("name");
				if (!name.startsWith("catma")) {
					// System.err.println(name);
					if (featureTypes == null || featureTypes.contains(name)) {
						b.append('+');
						b.append(name);
						if (pElement.hasText()) {
							b.append("=");
							b.append(pElement.text());
						}
					}
				}
			}
			propertiesMap.put(e.attr("xml:id"), b.toString());
		});

		reader.addRule("seg", Seg.class, (seg, element) -> {
			String anaAttribute = element.attr("ana");
			for (String ana : anaAttribute.split(" ")) {
				annoMap.put(ana.substring(1), seg);
			}
		});

		reader.read(jcas, is);

		// SimplePipeline.runPipeline(jcas,
		// AnalysisEngineFactory.createEngineDescription(BreakIteratorSegmenter.class,
		// BreakIteratorSegmenter.PARAM_WRITE_SENTENCE, false));

		for (String id : annoMap.keySet()) {
			// System.err.println(id);
			String typeId = fsId2typeId.get(id);
			if (typeId2description.containsKey(typeId)) {
				// System.err.println(typeId2description.containsKey(id));
				CatmaAnnotation ca = new CatmaAnnotation(jcas);
				ca.setBegin(annoMap.get(id).toList().getFirst().getBegin());
				ca.setEnd(annoMap.get(id).toList().getLast().getEnd());
				ca.setId(id);
				if (propertiesMap.containsKey(id))
					ca.setProperties(propertiesMap.get(id));
				ca.setCatmaType(typeId2description.get(typeId));
				ca.addToIndexes();
			}

		}

		// SimplePipeline.runPipeline(jcas,
		// AnalysisEngineFactory.createEngineDescription(AnnotationMerger.class));

		return jcas;
	}

	private ImmutableList<File> getFiles(File directory, String fileSuffix, boolean recursive) {
		MutableList<File> files = Lists.mutable.empty();
		for (File f : directory.listFiles()) {
			if (f.getName().endsWith(fileSuffix))
				files.add(f);
			else if (f.isDirectory()) {
				if (recursive)
					files.addAll(getFiles(f, fileSuffix, recursive).castToCollection());
			}
		}
		return files.toImmutable();
	}
}
