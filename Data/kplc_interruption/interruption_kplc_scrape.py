from lxml import html
import requests

from BeautifulSoup import BeautifulSoup




for page_num in range(1,3):
   url='http://kplc.co.ke/category/view/50/power-interruptions?page=' + str(page_num)
   print url
   page = requests.get(url)
   soup = BeautifulSoup(page.text)
   for a in soup.find_all('a', href=True):
    print "Found the URL:", a['href']
#   tree = html.fromstring(page.content)
#   links = tree.xpath('//a/@href')
#   for link in links:
#      if "interruption" in link and "page" not in link:
#         print link
