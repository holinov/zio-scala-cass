show:
	@echo "*** Get Rich API project top ***"
	@echo "make inv	- invalidate Elastic cache"
	@echo "make cov	- generate Coverage Report"
	@echo "make purge	- remove logs"
	@echo "make clean	- remove logs and all generated files"

# Invalidate Elastic cache locally
inv:
	@curl -XDELETE "http://127.0.0.1:9200/_all"

vs:
	@rm -rf .bloop .metals project/.bloop

purge:
	@rm -rf *.log ddata-gr_*

clean:purge
	@find . -name "target" | xargs rm -rf {} \;

rm:vs clean
	@echo
