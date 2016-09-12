var gulp = require('gulp');
var RevAll = require('gulp-rev-all');
var argv = require('yargs').argv;
var gzip = require('gulp-gzip');
var jsonTransform = require('gulp-json-transform');
var merge = require('merge-stream');
var gulpIgnore = require('gulp-ignore');
var debug = require('gulp-debug');
var runSequence = require('run-sequence');
var shell = require('gulp-shell');
var del = require('del');
var postcss = require("gulp-postcss");
var uglify = require('gulp-uglify');

gulp.task('css', function () {
  return gulp.src(['./resources/css/*.css'])
    .pipe(postcss([
      require('postcss-import')(),
      require('postcss-custom-media')(),
      require('postcss-custom-properties')(),
      require('postcss-calc')(),
      require('postcss-color-function')(),
      require('postcss-discard-comments')(),
      require('autoprefixer')({browsers: ['last 3 versions']}),
      /* require('postcss-reporter')(), */
      /* comment out cssnano to see uncompressed css */
      require('cssnano')()
    ]))
    .pipe(gulp.dest('./resources/public/css'));
});

gulp.task('watch', ['css'], function (cb) {
  gulp.watch('./resources/css/*.css', ['css']);
});

gulp.task('default', ['css']);

gulp.task('refresh-deps', function () {
  /* Run this after you update node module versions. */
  /* Maybe there's a preferred way of including node modules in cljs projects? */
  gulp.src(['./node_modules/react-slick/dist/react-slick.js'])
      .pipe(gulp.dest('src-cljs/storefront/'));
});

gulp.task('minify-js', function () {
  del(['./target/min-js']);

  gulp.src('src-cljs/storefront/*.js')
    .pipe(uglify())
    .pipe(gulp.dest('target/min-js/'));
});

gulp.task('cljs-build', shell.task(['lein cljsbuild once release']));

gulp.task('copy-release-assets', function () {
  return gulp.src(['./target/release/**'])
    .pipe(gulp.dest('./resources/public/'));
});

gulp.task('cdn', function () {
  if (!argv.host) {
    throw "missing --host";
  }

  // Clean up from last build
  del(['./resources/public/cdn', './resources/rev-manifest.json']);

  var revAll = new RevAll({
      prefix: "//" + argv.host + "/cdn/",
      dontSearchFile: ['.js']
  });

  var sourceMapPath = 'resources/public/js/out/main.js.map';
  var sourceMapStream = gulp.src([sourceMapPath])
      .pipe(jsonTransform(function(data) {
        data["sources"] = data["sources"].map(function(f) {
          return f.replace("\/", "/");
        });
        return data;
      }));

  var fileStream = gulp.src('resources/public/{js,css,images,fonts}/**')
      .pipe(gulpIgnore.exclude("*.map"));

  return merge(fileStream, sourceMapStream)
    .pipe(revAll.revision())
    .pipe(gzip({ append: false }))
    .pipe(gulp.dest('./resources/public/cdn'))
    .pipe(revAll.manifestFile())
    .pipe(gulp.dest('./resources'));
});

gulp.task('compile-assets', function(cb) {
  runSequence('css', 'minify-js', 'cljs-build', 'copy-release-assets', 'cdn', cb);
});
