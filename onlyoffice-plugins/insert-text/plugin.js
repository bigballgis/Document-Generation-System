(function(window, undefined) {
  window.Asc.plugin.init = function() {};

  window.Asc.plugin.onExternalPluginMessage = function(data) {
    if (!data || !data.type) return;

    switch (data.type) {
      case "insertText": {
        Asc.scope.text = data.text;
        this.callCommand(function() {
          var oDocument = Api.GetDocument();
          var oParagraph = Api.CreateParagraph();
          oParagraph.AddText(Asc.scope.text);
          oDocument.InsertContent([oParagraph]);
        }, false);
        break;
      }
    }
  };
})(window, undefined);
