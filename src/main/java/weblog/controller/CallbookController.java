package weblog.controller;

import java.util.Collection;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;

import QRZClient2.QRZCallsignNotFoundException;
import QRZClient2.QRZClient2;
import QRZClient2.QRZLookupResponse;
import weblog.CallbookEntryRepository;
import weblog.model.CallbookEntry;
import weblog.service.CallbookService;

@RestController
@RequestMapping(path="/callbook")
public class CallbookController {
	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private QRZClient2 qrzClient = new QRZClient2();
	
	@Autowired
	private CallbookEntryRepository callbookEntryRepository;
	
	@Autowired
	private CallbookService callbookService;
	
	public void addNewContact (CallbookEntry callbookEntry) {
		callbookEntryRepository.save(callbookEntry);
		logger.info("Callbook entry saved");
	}
	
	@GetMapping(path="/")
	public @ResponseBody Iterable<CallbookEntry> getAllCallbookEntries() {
		// This returns a JSON or XML with the users
		return callbookService.getAllCallbookEntries();
	}
	
	@GetMapping(path="/qrz/{callsign}")
	public @ResponseBody QRZLookupResponse getQrzCallbookEntryByCallsign(@PathVariable String callsign) throws QRZCallsignNotFoundException {
		return qrzClient.lookupCallsign(callsign);
	}
	
	@GetMapping(path="/{callsign}")
	public @ResponseBody Collection<CallbookEntry> getCallbookEntryByCallsign(@PathVariable String callsign) {
		return callbookService.getCallbookEntryByCallsign(callsign);
	}
}