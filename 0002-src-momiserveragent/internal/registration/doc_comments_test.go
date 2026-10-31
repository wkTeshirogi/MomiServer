package registration

import (
	"go/ast"
	"go/parser"
	"go/token"
	"os"
	"strings"
	"testing"
)

// TestGoDocCommentsCoverPublicIdentifiers は全OSの運用コードの公開宣言とパッケージ説明を検査する。
func TestGoDocCommentsCoverPublicIdentifiers(t *testing.T) {
	entries, err := os.ReadDir(".")
	if err != nil {
		t.Fatal(err)
	}
	packageDocs := 0
	for _, entry := range entries {
		name := entry.Name()
		if entry.IsDir() || !strings.HasSuffix(name, ".go") || strings.HasSuffix(name, "_test.go") {
			continue
		}
		file, err := parser.ParseFile(token.NewFileSet(), name, nil, parser.ParseComments)
		if err != nil {
			t.Fatal(err)
		}
		if file.Doc != nil {
			packageDocs++
			checkIdentifierDoc(t, name, "Package registration", file.Doc)
		}
		for _, comment := range file.Comments {
			for _, tag := range []string{"@param", "@return", "@throws", "@see"} {
				if strings.Contains(comment.Text(), tag) {
					t.Errorf("%s: JavaDoc tag %s is not a Go doc comment", name, tag)
				}
			}
		}
		for _, declaration := range file.Decls {
			switch decl := declaration.(type) {
			case *ast.FuncDecl:
				if decl.Name.IsExported() {
					checkIdentifierDoc(t, name, decl.Name.Name, decl.Doc)
				}
			case *ast.GenDecl:
				for _, spec := range decl.Specs {
					switch item := spec.(type) {
					case *ast.TypeSpec:
						doc := item.Doc
						if doc == nil {
							doc = decl.Doc
						}
						if item.Name.IsExported() {
							checkIdentifierDoc(t, name, item.Name.Name, doc)
						}
						checkStructFieldDocs(t, name, item.Type)
					case *ast.ValueSpec:
						for _, id := range item.Names {
							if !id.IsExported() {
								continue
							}
							if item.Doc == nil && decl.Lparen.IsValid() && decl.Doc != nil {
								// 同じ目的のグループ宣言は共通契約の説明を許可する。
								continue
							}
							doc := item.Doc
							if doc == nil {
								doc = decl.Doc
							}
							checkIdentifierDoc(t, name, id.Name, doc)
						}
					}
				}
			}
		}
	}
	if packageDocs != 1 {
		t.Errorf("registration requires one package doc comment, got %d", packageDocs)
	}
}

func checkStructFieldDocs(t *testing.T, file string, expression ast.Expr) {
	t.Helper()
	structure, ok := expression.(*ast.StructType)
	if !ok {
		return
	}
	for _, field := range structure.Fields.List {
		for _, name := range field.Names {
			if name.IsExported() {
				checkIdentifierDoc(t, file, name.Name, field.Doc)
			}
		}
		if len(field.Names) == 0 {
			if embedded, ok := field.Type.(*ast.SelectorExpr); ok && embedded.Sel.IsExported() {
				checkIdentifierDoc(t, file, embedded.Sel.Name, field.Doc)
			}
		}
		checkStructFieldDocs(t, file, field.Type)
	}
}

func checkIdentifierDoc(t *testing.T, file, name string, doc *ast.CommentGroup) {
	t.Helper()
	if doc == nil || !strings.HasPrefix(doc.Text(), name+" ") {
		t.Errorf("%s: doc comment must start with %q", file, name+" ")
	}
}
