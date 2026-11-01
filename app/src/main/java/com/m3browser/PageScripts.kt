package com.m3browser

/** Scripts injected into the page: chrome insets (including fixed footers) and translation hooks. */
object PageScripts {
    fun bootstrap(tabId: Long): String = """
        (function(){
          if (!window.__m3Boot) {
            window.__m3Boot = true;
            window.__m3Map = new Map();
            window.__m3Seq = 0;
            window.__m3TrOn = false;
            window.__m3Applying = false;
            window.__m3SetInset = function(topPx, bottomPx, stick) {
              window.__m3Top = topPx; window.__m3Bottom = bottomPx;
              var dpr = window.devicePixelRatio || 1;
              var top = topPx / dpr, bottom = bottomPx / dpr;
              var style = document.getElementById('m3-inset-style');
              if (!style) {
                style = document.createElement('style');
                style.id = 'm3-inset-style';
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = 'html{scroll-padding-top:'+top+'px!important;scroll-padding-bottom:'+bottom+'px!important}'
                + 'body{padding-top:'+top+'px!important;padding-bottom:'+bottom+'px!important;box-sizing:border-box!important}';
              if (!document.body) return;
              var vh = window.innerHeight || 1;
              var nodes = document.body.querySelectorAll('*');
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                var cs = getComputedStyle(el);
                if (cs.position !== 'fixed' && cs.position !== 'sticky') {
                  if (el.dataset.m3Lift === '1') {
                    el.style.transform = el.dataset.m3OrigTf || '';
                    el.dataset.m3Lift = '0';
                  }
                  continue;
                }
                var rect = el.getBoundingClientRect();
                if (rect.height > vh * 0.7) continue;
                var hit = bottom > 0 && (el.dataset.m3Lift === '1' || rect.bottom > vh - bottom - 1);
                if (hit) {
                  if (el.dataset.m3Lift !== '1') {
                    el.dataset.m3OrigTf = el.style.transform || '';
                    el.dataset.m3Lift = '1';
                  }
                  el.style.setProperty('transform', 'translateY(' + (-bottom) + 'px)', 'important');
                } else if (el.dataset.m3Lift === '1' && bottom <= 0) {
                  el.style.transform = el.dataset.m3OrigTf || '';
                  el.dataset.m3Lift = '0';
                }
              }
              if (stick) window.scrollTo(0, document.documentElement.scrollHeight || 0);
            };
            window.__m3Scan = function() {
              if (!window.__m3TrOn || !document.body || window.__m3Applying) return;
              var skip = {SCRIPT:1, STYLE:1, NOSCRIPT:1, TEXTAREA:1};
              var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
              var batch = [], node;
              while ((node = walker.nextNode())) {
                if (node.__m3done) continue;
                var parent = node.parentElement;
                if (!parent || skip[parent.tagName]) continue;
                var text = (node.nodeValue || '').replace(/\s+/g, ' ').trim();
                if (text.length < 2) continue;
                if (!node.__m3id) node.__m3id = 'n' + (++window.__m3Seq);
                window.__m3Map.set(node.__m3id, node);
                if (node.__m3orig == null) node.__m3orig = node.nodeValue;
                batch.push({id: node.__m3id, t: text.slice(0, 450)});
                if (batch.length >= 24) break;
              }
              if (batch.length && window.M3Browser) M3Browser.onBatch('$tabId', JSON.stringify(batch));
            };
            window.__m3Apply = function(json) {
              window.__m3Applying = true;
              try {
                var arr = JSON.parse(json);
                for (var i = 0; i < arr.length; i++) {
                  var node = window.__m3Map.get(arr[i].id);
                  if (!node) continue;
                  node.nodeValue = arr[i].t;
                  node.__m3out = arr[i].t;
                  node.__m3done = true;
                }
              } catch (e) {}
              window.__m3Applying = false;
              if (window.__m3TrOn) setTimeout(window.__m3Scan, 30);
            };
            window.__m3TrStart = function() { window.__m3TrOn = true; window.__m3Scan(); };
            window.__m3TrStop = function() {
              window.__m3TrOn = false;
              window.__m3Map.forEach(function(node) {
                if (node.__m3orig != null) node.nodeValue = node.__m3orig;
                node.__m3done = false;
              });
            };
            if (!window.__m3Obs && document.documentElement) {
              window.__m3Obs = new MutationObserver(function() {
                if (!window.__m3TrOn || window.__m3Pend) return;
                window.__m3Pend = true;
                setTimeout(function() { window.__m3Pend = false; window.__m3Scan && window.__m3Scan(); }, 280);
              });
              var root = document.body || document.documentElement;
              window.__m3Obs.observe(root, {childList: true, subtree: true, characterData: true});
            }
          }
          try {
            var lang = (document.documentElement && document.documentElement.lang) || '';
            var sample = '';
            if (!lang && document.body) sample = (document.body.innerText || '').slice(0, 500);
            if (window.M3Browser) M3Browser.onLang('$tabId', lang, sample);
          } catch (e) {}
        })();
    """.trimIndent()

    fun startTranslate(): String = "window.__m3TrStart && window.__m3TrStart();"
    fun stopTranslate(): String = "window.__m3TrStop && window.__m3TrStop();"
    fun applyTranslations(json: String): String = "window.__m3Apply && window.__m3Apply(${org.json.JSONObject.quote(json)});"
}
