package de.unistuttgart.ims.creta.catma2uima;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.fit.component.NoOpAnnotator;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.JCasIterable;
import org.apache.uima.fit.pipeline.JCasIterator;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.junit.Test;

import de.unistuttgart.ims.creta.catma2uima.types.CatmaAnnotation;

public class TestCATMA5InputReader {

	String directory = "src/test/resources";

	@Test
	public void testFile1() throws ResourceInitializationException {
		String filename = "file1.xml";
		JCasIterable iterable = SimplePipeline
				.iteratePipeline(CollectionReaderFactory.createReaderDescription(CATMA5InputReader.class,
						CATMA5InputReader.PARAM_INPUT_DIRECTORY, directory, CATMA5InputReader.PARAM_FILE_SUFFIX,
						filename), AnalysisEngineFactory.createEngineDescription(NoOpAnnotator.class));
		JCasIterator iterator = iterable.iterator();
		assertTrue(iterator.hasNext());
		JCas jcas = iterator.next();
		assertNotNull(jcas);
		AnnotationIndex<CatmaAnnotation> aIndex = jcas.getAnnotationIndex(CatmaAnnotation.class);
		assertFalse(aIndex.isEmpty());
		CatmaAnnotation ca;

		ca = JCasUtil.selectByIndex(jcas, CatmaAnnotation.class, 0);
		assertEquals("narrative", ca.getCatmaType());
		assertEquals("+narrative_name+ID_number=1", ca.getProperties());

		ca = JCasUtil.selectByIndex(jcas, CatmaAnnotation.class, 1);
		assertEquals("embedded narrative", ca.getCatmaType());
		assertEquals("+ID_number=2", ca.getProperties());

		ca = JCasUtil.selectByIndex(jcas, CatmaAnnotation.class, 2);
		assertEquals("narrative", ca.getCatmaType());
		assertEquals("+narrative_name+ID_number=1", ca.getProperties());

		ca = JCasUtil.selectByIndex(jcas, CatmaAnnotation.class, 5);
		assertEquals("embedded narrative", ca.getCatmaType());
		assertEquals("+ID_number=3", ca.getProperties());

	}
}
