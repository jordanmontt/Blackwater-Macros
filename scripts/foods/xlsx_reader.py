import zipfile, re, xml.etree.ElementTree as ET
NS={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
def col(ref):
    letters=re.match(r'[A-Z]+',ref).group(0); n=0
    for c in letters: n=n*26+ord(c)-64
    return n-1
def read(path, sheet='xl/worksheets/sheet1.xml'):
    z=zipfile.ZipFile(path)
    shared=[]
    if 'xl/sharedStrings.xml' in z.namelist():
        for si in ET.fromstring(z.read('xl/sharedStrings.xml')).findall('m:si',NS):
            shared.append(''.join(t.text or '' for t in si.iter('{%s}t'%NS['m'])))
    rows=[]
    for r in ET.fromstring(z.read(sheet)).iter('{%s}row'%NS['m']):
        row={}
        for c in r.findall('m:c',NS):
            v=c.find('m:v',NS); t=c.get('t')
            if t=='inlineStr':
                val=''.join(x.text or '' for x in c.iter('{%s}t'%NS['m']))
            elif v is None: continue
            else: val=shared[int(v.text)] if t=='s' else v.text
            row[col(c.get('r'))]=val
        rows.append([row.get(i) for i in range(max(row)+1)] if row else [])
    return rows
