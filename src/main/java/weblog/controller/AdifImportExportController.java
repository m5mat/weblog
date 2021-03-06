package weblog.controller;

import java.io.IOException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import weblog.StorageFileNotFoundException;
import weblog.service.StorageService;

@Controller
@RequestMapping(path="/adif")
public class AdifImportExportController {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());

    private final StorageService storageService;

    @Autowired
    public AdifImportExportController(@Qualifier("ADIFStorageService") StorageService storageService) {
        this.storageService = storageService;
    }
    
    @GetMapping("")
    public String listUploadedFiles(Model model) throws IOException {
/*
        model.addAttribute("files", storageService.loadAll().map(
                path -> MvcUriComponentsBuilder.fromMethodName(ImportExportController.class,
                        "serveFile", path.getFileName().toString()).build().toString())
                .collect(Collectors.toList()));
*/
    	model.addAttribute("submitUrl", "/adif");
        return "importexport";
    }
    
    @GetMapping("/download/{id}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable Long id) {
        Resource file = storageService.loadAsResource(id, "dummy");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    }
    
    /*
    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    }
    */
    
    /*
     * Accept an uploaded file, create a new log for it and store the file's entries
     * as contacts in the log.
     */
    @PostMapping("")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {

    	logger.info("File uploaded " + file.getOriginalFilename());
    	
        storageService.store(file);
        redirectAttributes.addFlashAttribute("message",
                "You successfully uploaded " + file.getOriginalFilename() + "!");

        return "redirect:/adif/";
    }
    
    /*
     * Accept multiple uploaded files, create a new logs for them and store the file's entries
     * as contacts in the log.
     */
    @PostMapping("/multi")
    public String handleMultipleFileUpload(@RequestParam("file") MultipartFile[] files, RedirectAttributes redirectAttributes) {

    	for(MultipartFile file : files) {
	    	logger.info("File uploaded " + file.getOriginalFilename());
	    	
	        storageService.store(file);
	        redirectAttributes.addFlashAttribute("message",
	                "You successfully uploaded " + file.getOriginalFilename() + "!");
    	}
        return "redirect:/adif/";
    }
    
    

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }
}
