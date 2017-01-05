import PyPDF2
import re

pdfFileObj = open('test.pdf', 'rb')
pdfReader = PyPDF2.PdfFileReader(pdfFileObj)
num_pages = pdfReader.numPages
pageObj = pdfReader.getPage(0)
text = pageObj.extractText()
text = text.replace("\n","")



regions  = re.split('([A-Z]*(?=\s[A-Z])(?:\sREGION)+)', text) #look for one word in all caps and then REGION in all caps
header = ""
for region in regions:
   print "----------------------------"
   if len(region) <= 100: #so my regex skills are not all that... mt kenya region doesn't get caught while coast region gets caught... have to manually check this in the loop now
      header += region
   else:
      print header.strip() 
      header = "" 
      areas = region.split("AREA: ") #data is here
      for area in areas:
         if len(area) >= 10:
            area_meta_end_char = re.search('(A.M.|P.M.).*(A.M.|P.M.)', area).end()
            meta = area[:area_meta_end_char]
            data = area[area_meta_end_char:]
            area_loc_end_char = meta.find("DATE")
            date_loc_end_char = meta.find("TIME")
            area_loc = meta[:area_loc_end_char]
            date = meta[area_loc_end_char+len("DATE: "):date_loc_end_char].strip()
            time = meta[date_loc_end_char:].strip()
            print area_loc + " : " + date + " : " + time
            sub_locs = data.split(",")
            for sub_loc in sub_locs:
               if not "Notice is hereby " in sub_loc and not "etc" in sub_loc:
                  print sub_loc.strip()
