import http from './http';

export type BlogStatus = 'DRAFT' | 'PUBLISHED';
export interface BlogPost {
  id:number; userId:number; title:string; slug:string; summary:string|null; markdownContent:string;
  coverUrl:string|null; category:string|null; tags:string|null; status:BlogStatus; pinned:boolean;
  seoTitle:string|null; seoDescription:string|null; viewCount:number; publishedAt:string|null;
  createdAt:string; updatedAt:string;
}
export interface BlogPostSummary {
  id:number; title:string; slug:string; summary:string|null; coverUrl:string|null; category:string|null;
  tags:string|null; pinned:boolean; viewCount:number; publishedAt:string; updatedAt:string;
}
export interface BlogPostPayload {
  title:string; slug:string; summary:string; markdownContent:string; coverUrl:string; category:string;
  tags:string; pinned:boolean; seoTitle:string; seoDescription:string;
}
export interface BlogArchive { posts:BlogPostSummary[]; categories:string[]; tags:string[]; }

export const listBlogPosts=(params:Record<string,unknown>)=>http.get<unknown,{data:BlogPost[]}>('/blog/admin/posts',{params});
export const getBlogPost=(id:number)=>http.get<unknown,{data:BlogPost}>(`/blog/admin/posts/${id}`);
export const createBlogPost=(data:BlogPostPayload)=>http.post<unknown,{data:number}>('/blog/admin/posts',data);
export const updateBlogPost=(id:number,data:BlogPostPayload)=>http.put(`/blog/admin/posts/${id}`,data);
export const publishBlogPost=(id:number)=>http.post(`/blog/admin/posts/${id}/publish`);
export const unpublishBlogPost=(id:number)=>http.post(`/blog/admin/posts/${id}/unpublish`);
export const deleteBlogPost=(id:number)=>http.delete(`/blog/admin/posts/${id}`);
export const uploadBlogAsset=(id:number,file:File)=>{const data=new FormData();data.append('file',file);return http.post<unknown,{data:{id:number;url:string}}>(`/blog/admin/posts/${id}/assets`,data);};
export const listPublicBlog=(params:Record<string,unknown>)=>http.get<unknown,{data:BlogArchive}>('/public/blog/posts',{params});
export const getPublicBlogPost=(slug:string)=>http.get<unknown,{data:BlogPost}>(`/public/blog/posts/${encodeURIComponent(slug)}`);
