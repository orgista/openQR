/* @ds-bundle: {"format":4,"namespace":"OpenQR","components":[{"name":"PillReader"},{"name":"CodeTag"},{"name":"NoticeStack"},{"name":"ScanToggle"},{"name":"ActionButton"},{"name":"CodeChip"},{"name":"Icon"}]} */
(function () {
  var React = window.React, h = React.createElement;
  function cx() { return Array.prototype.filter.call(arguments, Boolean).join(' '); }

  // Source icons (app/src/main/res/drawable, filled, 24 grid) and stand-ins for
  // the Meta UI Set icons the 1.1 screens use (stroked, 24 grid).
  var ICONS = {
    scan: { fill: 'M4,4h4v2H6v2H4zM16,4h4v4h-2V6h-2zM4,16h2v2h2v2H4zM18,16h2v4h-4v-2h2zM8.5,8.5h7v7h-7zM10,10h4v4h-4z' },
    check: { fill: 'M9.55,16.6L5.4,12.45l1.4,-1.4 2.75,2.75 7,-7 1.4,1.4z' },
    warning: { fill: 'M1,21h22L12,2zM13,18h-2v-2h2zM13,14h-2v-5h2z', evenodd: true },
    lock: { fill: 'M17,9h-1V7a4,4 0 0 0 -8,0v2H7a2,2 0 0 0 -2,2v8a2,2 0 0 0 2,2h10a2,2 0 0 0 2,-2v-8a2,2 0 0 0 -2,-2zM10,7a2,2 0 0 1 4,0v2h-4z', evenodd: true },
    camera: { fill: 'M9,3L7.17,5H4c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2h-3.17L15,3H9zM12,16c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4s4,1.79 4,4s-1.79,4 -4,4z', evenodd: true },
    internet: { stroke: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9 M12 3c-2.5 2.6-3.8 5.6-3.8 9s1.3 6.4 3.8 9' },
    youtube: { stroke: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M10 8.5v7l5.5-3.5z' },
    store: { stroke: 'M5 8h14l-1 12H6z M9 8V6.5a3 3 0 0 1 6 0V8' },
    app: { stroke: 'M5 5h5v5H5z M14 5h5v5h-5z M5 14h5v5H5z M14 14h5v5h-5z' },
    wifi: { stroke: 'M2.5 9a14 14 0 0 1 19 0 M5.5 12.5a9.5 9.5 0 0 1 13 0 M8.8 15.8a5 5 0 0 1 6.4 0 M12 19.2h.01' },
    bluetooth: { stroke: 'M7 7l10 10-5 4V3l5 4L7 17' },
    unsupported: { stroke: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 7.5v5.5 M12 16.5h.01' },
    stop: { stroke: 'M7 7h10v10H7z' },
    play: { stroke: 'M8 5.5v13l10-6.5z' },
    open: { stroke: 'M14 4h6v6 M20 4l-9 9 M18 14v6H4V6h6' },
    positive: { stroke: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M8 12.3l2.7 2.7L16 9.7' },
    negative: { stroke: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M9 9l6 6 M15 9l-6 6' }
  };
  var KIND_LABEL = { web: 'Open link', youtube: 'YouTube', store: 'Meta Horizon Store', app: 'Open app', wifi: 'Join network', bluetooth: 'Pair device', unsupported: "Can't open" };
  var KIND_WORD = { web: 'Website', youtube: 'Video', store: 'Store page', app: 'App link', wifi: 'Wi-Fi network', bluetooth: 'Bluetooth device', unsupported: 'Unsupported code' };
  var KIND_ICON = { web: 'internet', youtube: 'youtube', store: 'store', app: 'app', wifi: 'wifi', bluetooth: 'bluetooth', unsupported: 'unsupported' };

  function Icon(props) {
    var spec = ICONS[props.name] || ICONS.scan, size = props.size || 24;
    var path = spec.fill
      ? h('path', { d: spec.fill, fill: 'currentColor', fillRule: spec.evenodd ? 'evenodd' : 'nonzero' })
      : h('path', { d: spec.stroke, fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round' });
    return h('svg', {
      className: cx('oq-icon', props.className), width: size, height: size, viewBox: '0 0 24 24',
      role: props.title ? 'img' : undefined, 'aria-hidden': props.title ? undefined : true, 'aria-label': props.title
    }, path);
  }

  function ActionButton(props) {
    var variant = props.variant || 'primary';
    return h('button', {
      type: 'button', disabled: props.disabled, onClick: props.onClick, 'aria-pressed': props.active ? true : undefined,
      className: cx('oq-btn', 'oq-btn-' + variant, props.active && 'oq-btn-active', props.expanded && 'oq-btn-expanded', props.className)
    }, props.icon ? h(Icon, { name: props.icon, size: 20 }) : null, h('span', null, props.children));
  }

  /** A small turning arc: the hand-off is in progress. Still under reduced motion. */
  function Spinner(props) {
    var size = props.size || 20;
    return h('svg', { className: 'oq-spin', width: size, height: size, viewBox: '0 0 24 24', 'aria-hidden': true },
      h('circle', { className: 'oq-spin-track', cx: 12, cy: 12, r: 9 }),
      h('circle', { className: 'oq-spin-arc', cx: 12, cy: 12, r: 9 }));
  }

  /** One decoded code: a phone-camera style button on the code, or a row in the Scanned list. */
  function CodeChip(props) {
    var kind = props.kind || 'web', label = props.label || KIND_LABEL[kind];
    var enabled = kind !== 'unsupported' && props.enabled !== false && !props.opening;
    var text = props.opening ? 'Opening ' + label : label;
    return h('button', {
      type: 'button', disabled: !enabled, onClick: props.onClick, 'aria-label': text, 'aria-busy': props.opening || undefined,
      className: cx('oq-chip', props.variant === 'row' ? 'oq-chip-row' : 'oq-chip-on-code', props.inView === false && 'oq-chip-out', props.opening && 'oq-chip-opening')
    }, props.opening ? h(Spinner) : h(Icon, { name: KIND_ICON[kind], size: 20 }), h('span', { className: 'oq-chip-label' }, text));
  }

  /**
   * The minimal pill's one control. On: a ring sweeps around the scan glyph and
   * hover/focus shows Stop; a tap turns scanning off. Off: a still play glyph.
   */
  function ScanToggle(props) {
    var on = props.on !== false;
    var label = props.label || (on ? 'Scanning. Stop' : 'Scan');
    return h('span', { className: 'oq-pi-wrap' },
      h('button', {
        type: 'button', className: cx('oq-st', on ? 'oq-st-on' : 'oq-st-off'), 'aria-label': label,
        'aria-pressed': on, onClick: props.onClick
      },
        on ? h('svg', { className: 'oq-st-ring', viewBox: '0 0 56 56', 'aria-hidden': true },
          h('circle', { className: 'oq-st-track', cx: 28, cy: 28, r: 26.5 }),
          h('circle', { className: 'oq-st-arc', cx: 28, cy: 28, r: 26.5 })) : null,
        on ? h(Icon, { name: 'scan', className: 'oq-st-glyph-on' }) : null,
        on ? h(Icon, { name: 'stop', className: 'oq-st-glyph-stop' }) : h(Icon, { name: 'play' })),
      h('span', { className: 'oq-tip', role: 'presentation' }, label));
  }

  /**
   * A tag drawn on a real QR code in passthrough: corner brackets on the code
   * and a destination chip just below it. Point and click the chip to open.
   */
  function CodeTag(props) {
    var state = props.state || 'found', size = props.size || 120;
    var kind = state === 'unsupported' ? 'unsupported' : (props.kind || 'web');
    return h('div', {
      className: cx('oq-tag', 'oq-tag-' + state), style: { width: size, height: size },
      'aria-label': state === 'opening' ? 'Opening ' + (props.label || KIND_LABEL[kind]) : (props.label || KIND_LABEL[kind])
    },
      ['tl', 'tr', 'bl', 'br'].map(function (c) { return h('span', { key: c, className: 'oq-tag-br oq-tag-br-' + c }); }),
      h('div', { className: 'oq-tag-chip' },
        h(CodeChip, { kind: kind, label: props.label, opening: state === 'opening', onClick: props.onClick })));
  }

  /**
   * The fallback when Spatial Data isn't available: codes the camera decodes
   * appear as notices stacked above the pill, newest nearest the pill.
   * Each notice is one button: point at it and click to open.
   */
  function NoticeStack(props) {
    var notices = props.notices || [], max = props.max || 3;
    var shown = notices.slice(-max), hidden = notices.length - shown.length;
    return h('div', { className: 'oq-notices', role: 'list', 'aria-label': 'Codes found' },
      hidden > 0 ? h('div', { className: 'oq-notice-more' }, hidden + ' more') : null,
      shown.map(function (n, i) {
        var state = n.state || 'inView';
        var kind = state === 'unsupported' ? 'unsupported' : (n.kind || 'web');
        var label = n.label || KIND_LABEL[kind];
        var title = state === 'opening' ? 'Opening ' + label : state === 'unsupported' ? "Can't open this code" : label;
        var sub = state === 'lingering' ? 'Out of view' : KIND_WORD[kind];
        return h('div', { key: n.id || i, role: 'listitem' },
          h('button', {
            type: 'button', className: cx('oq-notice', 'oq-notice-' + state),
            disabled: state === 'unsupported' || state === 'opening', 'aria-busy': state === 'opening' || undefined,
            'aria-label': title + (state === 'unsupported' || state === 'opening' ? '' : '. Open'),
            onClick: function () { props.onOpen && props.onOpen(n, i); }
          },
            h('span', { className: 'oq-notice-icon' }, state === 'opening' ? h(Spinner, { size: 24 }) : h(Icon, { name: KIND_ICON[kind] })),
            h('span', { className: 'oq-notice-text' },
              h('span', { className: 'oq-notice-title' }, title),
              h('span', { className: 'oq-notice-sub' }, sub)),
            state === 'inView' || state === 'lingering' ? h(Icon, { name: 'open', size: 20, className: 'oq-notice-go' }) : null));
      }));
  }

  var MODE_LABEL = { scanning: 'Scanning. Stop', paused: 'Paused. Resume', off: 'Scan' };

  /**
   * The always-on pill. It holds only the scan toggle, or one line for a
   * system message; codes found appear as notices above it (NoticeStack).
   */
  function PillReader(props) {
    var mode = props.mode || 'scanning', status = props.status;
    if (mode === 'status' && status) {
      var busy = status.kind === 'opening';
      return h('div', { className: cx('oq-pill', status.action ? 'oq-pill-act' : 'oq-pill-note'), role: 'status', 'aria-label': status.title },
        h('span', { className: busy ? 'oq-tone-busy' : 'oq-tone-' + (status.kind || 'positive') },
          busy ? h(Spinner, { size: 24 }) : h(Icon, { name: status.kind || 'positive' })),
        h('span', { className: 'oq-pill-title' }, status.title),
        status.action ? h(ActionButton, { onClick: props.onAction }, status.action.label) : null);
    }
    return h('div', { className: 'oq-pill', role: 'toolbar', 'aria-label': 'OpenQR' },
      h(ScanToggle, { on: mode === 'scanning', label: MODE_LABEL[mode], onClick: props.onToggleScan }));
  }

  window.OpenQR = Object.assign(window.OpenQR || {}, {
    PillReader: PillReader, CodeTag: CodeTag, NoticeStack: NoticeStack, ScanToggle: ScanToggle, ActionButton: ActionButton, CodeChip: CodeChip, Icon: Icon
  });
})();
