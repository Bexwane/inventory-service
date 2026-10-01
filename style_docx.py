import sys
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt
from docx.oxml.ns import qn

def style_document(file_path):
    doc = Document(file_path)

    style = doc.styles['Normal']
    style.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    
    font = style.font
    font.name = 'Arial'
    font.size = Pt(11)

    for i in range(1, 6):
        heading_style_name = f'Heading {i}'
        if heading_style_name in doc.styles:
            h_style = doc.styles[heading_style_name]
            h_font = h_style.font
            h_font.name = 'Arial'

    doc.save(file_path)
    print("Document successfully styled with justified alignment and standard fonts.")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        style_document(sys.argv[1])
    else:
        print("Usage: python style_docx.py <file.docx>")
