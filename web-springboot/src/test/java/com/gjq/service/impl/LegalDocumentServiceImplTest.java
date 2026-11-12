package com.gjq.service.impl;

import com.gjq.client.AlgorithmClient;
import com.gjq.common.exception.BusinessException;
import com.gjq.mapper.LegalDocumentChunkIndexMapper;
import com.gjq.mapper.LegalDocumentChunkMapper;
import com.gjq.mapper.LegalDocumentExtMapper;
import com.gjq.mapper.LegalDocumentMapper;
import com.gjq.mapper.LegalDocumentPartyMapper;
import com.gjq.mapper.LegalDocumentReferenceMapper;
import com.gjq.service.CategoryService;
import com.gjq.service.LegalKnowledgeService;
import com.gjq.service.support.LegalDocumentChunkService;
import com.gjq.service.support.LegalDocumentParser;
import com.gjq.service.support.LegalDocumentRelationExtractor;
import com.gjq.service.support.LegalDocumentTextProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalDocumentServiceImplTest {

    @Mock
    private LegalDocumentMapper legalDocumentMapper;

    @Mock
    private LegalDocumentPartyMapper partyMapper;

    @Mock
    private LegalDocumentReferenceMapper referenceMapper;

    @Mock
    private LegalDocumentChunkMapper chunkMapper;

    @Mock
    private LegalDocumentChunkIndexMapper chunkIndexMapper;

    @Mock
    private LegalDocumentExtMapper extMapper;

    @Mock
    private CategoryService categoryService;

    @Mock
    private AlgorithmClient algorithmClient;

    @Mock
    private LegalKnowledgeService legalKnowledgeService;

    @Mock
    private LegalDocumentChunkService legalDocumentChunkService;

    @Mock
    private LegalDocumentParser legalDocumentParser;

    @Mock
    private LegalDocumentRelationExtractor legalDocumentRelationExtractor;

    @Mock
    private LegalDocumentTextProcessor legalDocumentTextProcessor;

    @InjectMocks
    private LegalDocumentServiceImpl legalDocumentService;

    private void useRealTextProcessor() {
        LegalDocumentTextProcessor realProcessor = new LegalDocumentTextProcessor();
        when(legalDocumentTextProcessor.collapseWhitespace(anyString())).thenAnswer(invocation ->
                realProcessor.collapseWhitespace(invocation.getArgument(0))
        );
        lenient().when(legalDocumentTextProcessor.ellipsize(anyString(), anyInt())).thenAnswer(invocation ->
                realProcessor.ellipsize(invocation.getArgument(0), invocation.getArgument(1))
        );
    }

    @Test
    void previewFileRejectsMissingFile() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> legalDocumentService.previewFile(null, null)
        );

        assertThat(exception.getCode()).isEqualTo(400);
    }

    @Test
    void previewFileRejectsUnsupportedExtractionResult() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                "sample".getBytes()
        );
        when(algorithmClient.extractDocumentText(file)).thenReturn(Map.of(
                "data", Map.of(
                        "supported", false,
                        "text", "sample"
                )
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> legalDocumentService.previewFile(file, null)
        );

        assertThat(exception.getCode()).isEqualTo(400);
    }

    @Test
    void previewFileRejectsEmptyExtractedText() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                "sample".getBytes()
        );
        when(algorithmClient.extractDocumentText(file)).thenReturn(Map.of(
                "data", Map.of(
                        "supported", true,
                        "text", "   "
                )
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> legalDocumentService.previewFile(file, null)
        );

        assertThat(exception.getCode()).isEqualTo(422);
    }

    @Test
    void previewFileMapsDependencyFailureToServiceUnavailable() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                "sample".getBytes()
        );
        when(algorithmClient.extractDocumentText(file)).thenThrow(
                new IllegalStateException("文档解析服务不可用")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> legalDocumentService.previewFile(file, null)
        );

        assertThat(exception.getCode()).isEqualTo(503);
        assertThat(exception.getMessage()).contains("文档解析服务不可用");
    }

    @Test
    void buildTitlePrefersFormalProsecutionHeaderOverPdfFileName() {
        useRealTextProcessor();

        String title = ReflectionTestUtils.invokeMethod(
                legalDocumentService,
                "buildTitle",
                "12309_PDF_文平运输毒品案起诉书_2025.pdf",
                List.of("云南省师宗县人民检察院", "起 诉 书", "师检刑诉〔2025〕54号")
        );

        assertThat(title).isEqualTo("云南省师宗县人民检察院起诉书");
    }

    @Test
    void buildTitleCleansGeneratedPdfFileNameWhenHeaderIsUnavailable() {
        useRealTextProcessor();

        String title = ReflectionTestUtils.invokeMethod(
                legalDocumentService,
                "buildTitle",
                "12309_PDF_文平运输毒品案起诉书_2025.pdf",
                List.of("师检刑诉〔2025〕54号")
        );

        assertThat(title).isEqualTo("文平运输毒品案起诉书");
    }

    @Test
    void extractCourtNameUsesProcuratorateHeaderAndIgnoresBodyCourtSentence() {
        useRealTextProcessor();
        String text = """
                云南省师宗县人民检察院
                起诉书
                被告人文平因犯帮助信息网络犯罪活动罪被湖南省长沙市岳麓区人民法院判处。
                """;

        String institution = ReflectionTestUtils.invokeMethod(
                legalDocumentService,
                "extractCourtName",
                List.of(
                        "云南省师宗县人民检察院",
                        "起诉书",
                        "被告人文平因犯帮助信息网络犯罪活动罪被湖南省长沙市岳麓区人民法院判处。"
                ),
                "云南省师宗县人民检察院起诉书",
                text
        );

        assertThat(institution).isEqualTo("云南省师宗县人民检察院");
    }

    @Test
    void detectsProsecutionDocumentTypeAndProcedureDomain() {
        String title = "云南省师宗县人民检察院起诉书";
        String text = "曲检刑诉〔2025〕54号\n本院认为，被告人文平涉嫌运输毒品罪，应当提起公诉。";

        String subtype = ReflectionTestUtils.invokeMethod(legalDocumentService, "detectDocumentSubtype", title, text);
        String type = ReflectionTestUtils.invokeMethod(legalDocumentService, "detectDocumentType", subtype, title, text);
        String procedureDomain = ReflectionTestUtils.invokeMethod(legalDocumentService, "detectProcedureDomain", title, text);
        String caseNumber = ReflectionTestUtils.invokeMethod(legalDocumentService, "extractCaseNumber", title, text);

        assertThat(subtype).isEqualTo("起诉书");
        assertThat(type).isEqualTo("起诉书");
        assertThat(procedureDomain).isEqualTo("审查起诉");
        assertThat(caseNumber).isEqualTo("曲检刑诉〔2025〕54号");
    }

    @Test
    void detectDocumentSubtypeDoesNotTreatBodyMentionAsIndictment() {
        String title = "云南省某某市中级人民法院刑事判决书";
        String text = "公诉机关在起诉书中指控被告人构成运输毒品罪，本院经审理查明后作出判决。";

        String subtype = ReflectionTestUtils.invokeMethod(legalDocumentService, "detectDocumentSubtype", title, text);
        String type = ReflectionTestUtils.invokeMethod(legalDocumentService, "detectDocumentType", subtype, title, text);

        assertThat(subtype).isEqualTo("刑事判决书");
        assertThat(type).isEqualTo("判决书");
    }
}
